"""Shared setup for the Infinite chat companion bot.

Structure follows CartelBot deliberately -- an Auth class reading a .env, a commands.Bot
subclass, and cogs auto-loaded from ./cogs -- so anyone who has worked on one can read the
other. What is not carried over is the economy database and the psutil sampling: this bot
holds no state of its own. Everything it reports is read from files the Minecraft server
writes, and everything it sends is handed to the server the same way.

Separate from CartelBot as its own process on purpose. CartelBot is a slash-command bot that
deliberately does NOT request the Message Content intent, which is what keeps it eligible for
Discord's App Discovery. Relaying chat requires that intent, so folding this into CartelBot
would have cost it that eligibility. Two bots, two tokens, neither compromised.
"""

import os
import time

import nextcord as discord
from nextcord.ext import commands
from dotenv import load_dotenv, find_dotenv

load_dotenv(find_dotenv(raise_error_if_not_found=True))


class Auth:
    TOKEN = os.getenv("TOKEN")
    # The channel the bridge listens to and posts about. Everything else is ignored.
    CHANNEL_ID = int(os.getenv("CHANNEL_ID") or 0)
    # Where the Minecraft server keeps the files the two sides exchange.
    WORLD_DIR = os.getenv("WORLD_DIR") or "/opt/infinite/server/world"


class BridgeBot(commands.Bot):
    def __init__(self, *args, **kwargs):
        kwargs.setdefault("help_command", None)
        super().__init__(*args, **kwargs)
        self.start_time = time.time()
        self.relayed_in = 0

    async def on_ready(self):
        print("=== INFINITE CHAT BRIDGE ===")
        for file in os.listdir("./cogs"):
            if file.endswith(".py") and not file.startswith("__"):
                ext = f"cogs.{file[:-3]}"
                if ext in self.extensions:
                    continue
                try:
                    self.load_extension(ext)
                    print(f"- {file[:-3]} loaded")
                except Exception as e:
                    print(f"- {file[:-3]} FAILED ({e})")

        if not Auth.CHANNEL_ID:
            print("WARNING: CHANNEL_ID is not set; nothing will be relayed.")
        if not os.path.isdir(Auth.WORLD_DIR):
            print(f"WARNING: {Auth.WORLD_DIR} does not exist; is this the right box?")

        await self.change_presence(
            status=discord.Status.online, activity=discord.Game("Infinite Reborn")
        )
        print(f"{self.user} online, bridging channel {Auth.CHANNEL_ID}")

        try:
            await self.sync_all_application_commands()
            print("slash commands synced")
        except Exception as e:
            print(f"slash sync failed: {e}")
