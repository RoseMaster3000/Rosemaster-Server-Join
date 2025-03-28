# Server First Join Command
I wrote this simple mod that lets you configure a list of commands to be run when a player joins the server for the first time.

## Command Block Cons
* Command blocks check the player list every tick. This is resource intesnive.
* Command blocks must be placed in the world manully / hidden under bedrock
* Command blocks need to be in a loaded chunk to function

## Features
* This mod injects into the [Player Join] event, and runs commands listed in the config file `server_join_commands.txt`.
* It will also give a [book](https://www.gamergeeks.net/apps/minecraft/give-command-generator/written-books) listed in `server_join_book.txt`
