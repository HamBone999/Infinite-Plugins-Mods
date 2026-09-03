"""Discord-side commands about the Minecraft server.

Everything here reads the status file the plugin writes; nothing queries the server directly.
This build has no RCON and no query port, and giving the bot a way to make the server do work
would mean a bot problem could become a server problem. Reading a file it cannot affect keeps
the coupling one way.
"""

import os
import time

import nextcord as discord
from nextcord.ext import commands

from base import Auth

STATUS = os.path.join(Auth.WORLD_DIR, "chatbridge-status.tsv")

# The plugin rewrites the file every 10s, so anything older than this means the server is not
# ticking -- stopped, crashed, or wedged.
STALE_AFTER = 60


def _read_status():
    """Returns (updated_epoch, online_count, [names]) or None if unreadable."""
    try:
        data = {}
        with open(STATUS, "r", encoding="utf-8") as f:
            for line in f:
                if "\t" in line:
                    k, v = line.rstrip("\n").split("\t", 1)
                    data[k] = v
        names = [n for n in (data.get("players") or "").split(",") if n]
        return int(data.get("updated") or 0), int(data.get("online") or 0), names
    except Exception:
        return None


class ServerInfo(commands.Cog):
    def __init__(self, bot):
        self.bot = bot

    @discord.slash_command(name="players", description="Who is on the Minecraft server")
    async def players(self, interaction: discord.Interaction):
        st = _read_status()
        if st is None:
            await interaction.response.send_message(
                "I cannot read the server status file. Is the server running?", ephemeral=True
            )
            return
        updated, count, names = st
        age = int(time.time()) - updated
        if age > STALE_AFTER:
            await interaction.response.send_message(
                f"The server looks down -- no update for {age // 60} minutes.", ephemeral=True
            )
            return
        if count == 0:
            await interaction.response.send_message("Nobody is online right now.")
            return
        await interaction.response.send_message(
            f"**{count} online:** " + ", ".join(names)
        )

    @discord.slash_command(name="status", description="Is the Minecraft server up?")
    async def status(self, interaction: discord.Interaction):
        st = _read_status()
        if st is None:
            await interaction.response.send_message("Server status unavailable.", ephemeral=True)
            return
        updated, count, _ = st
        age = int(time.time()) - updated
        up = age <= STALE_AFTER
        uptime = int(time.time() - self.bot.start_time)
        await interaction.response.send_message(
            ("**Server: up**" if up else f"**Server: down** (silent for {age // 60}m)")
            + f"\nPlayers online: {count}"
            + f"\nBridge uptime: {uptime // 3600}h {(uptime % 3600) // 60}m"
            + f"\nRelayed to Minecraft this run: {self.bot.relayed_in}"
        )


def setup(bot):
    bot.add_cog(ServerInfo(bot))
