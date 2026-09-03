import nextcord as discord

from base import BridgeBot, Auth

# message_content is the one privileged intent this needs: without it every message arrives
# with an empty body and the relay silently forwards blank lines. It has to be enabled for
# the application in the Discord developer portal as well as requested here.
intents = discord.Intents.default()
intents.message_content = True

client = BridgeBot(command_prefix="!ic ", intents=intents)

if __name__ == "__main__":
    print("Starting Infinite chat bridge...")
    client.run(Auth.TOKEN)
