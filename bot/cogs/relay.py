"""Discord -> Minecraft.

The bot hears a message on its gateway connection and writes it to a spool directory the
server drains on its next tick. One file per message, written under a temp name and renamed
into place: rename is atomic on the same filesystem, so the server never reads a half-written
message and deleting a file after reading it can never lose one that arrived mid-read.

The other direction is not handled here. Minecraft -> Discord goes through the plugin's
webhook, which posts with per-message names and avatars -- something a bot account cannot do
for messages that are not its own.
"""

import os
import time

import nextcord as discord
from nextcord.ext import commands

from base import Auth

SPOOL = os.path.join(Auth.WORLD_DIR, "chatbridge-spool")

# The Minecraft client kicks on an over-long chat packet, so this is a real limit rather than
# a tidiness one. The plugin truncates again on its side; this just avoids writing waste.
MAX_LEN = 180


def _clean(text: str) -> str:
    """Strip what Minecraft chat cannot show, or should not be told.

    The section sign is the important one: leaving it in would let anyone in Discord inject
    colour codes into the game, and more to the point forge a line that looks like it came
    from the server itself.
    """
    out = []
    for ch in text:
        if ch in "\n\r\t":
            out.append(" ")
        elif ch == "§" or ord(ch) < 32 or ord(ch) == 127:
            continue
        else:
            out.append(ch)
    return " ".join("".join(out).split())[:MAX_LEN]


class Relay(commands.Cog):
    def __init__(self, bot):
        self.bot = bot
        self._n = 0

    @commands.Cog.listener()
    async def on_message(self, message: discord.Message):
        if not Auth.CHANNEL_ID or message.channel.id != Auth.CHANNEL_ID:
            return
        # Ignore every bot, which includes the plugin's own webhook posts. Without this the
        # bridge would echo Minecraft chat straight back into Minecraft.
        if message.author.bot:
            return

        body = _clean(message.content or "")
        if not body and message.attachments:
            body = "(attachment)"
        if not body:
            return

        name = _clean(message.author.display_name or message.author.name)[:32]
        line = f"[Discord] {name}: {body}"

        try:
            self._write(line)
            self.bot.relayed_in += 1
        except Exception as e:
            print(f"relay: could not write to the spool: {e}")

    def _write(self, line: str) -> None:
        os.makedirs(SPOOL, exist_ok=True)
        self._n = (self._n + 1) % 10000
        # Name sorts chronologically, which is the order the server replays them in.
        stem = f"{int(time.time() * 1000):013d}-{self._n:04d}"
        tmp = os.path.join(SPOOL, stem + ".tmp")
        final = os.path.join(SPOOL, stem + ".msg")
        with open(tmp, "w", encoding="utf-8") as f:
            f.write(line + "\n")
        os.replace(tmp, final)


def setup(bot):
    bot.add_cog(Relay(bot))
