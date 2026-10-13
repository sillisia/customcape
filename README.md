# Custom Cape

A client-side Fabric mod for Minecraft 1.21.11 that lets you design your own cape and elytra in-game.

- **Design tools:** gradients, banner patterns, or your own images, with separate designs for the back (outside) and front (inside).
- **Profiles:** save as many designs as you like and switch between them.
- **Independent toggles:** turn the custom cape and custom elytra on or off separately. Anything turned off shows the normal Minecraft look.

## Install

Requires Minecraft 1.21.11, [Fabric Loader](https://fabricmc.net/use/) 0.18.2 or newer, and [Mod Menu](https://modrinth.com/mod/modmenu) 17.0.0. Fabric API is not required. Put the jar in `.minecraft/mods/`.

## Build from source

Requires **JDK 21**. The first build downloads Minecraft and needs internet access.

``` 
> powershell
git clone https://github.com/sillisia/customcape.git
cd customcape
& "$HOME\gradle-dist\gradle-9.2.0\bin\gradle.bat" wrapper --gradle-version 9.2.0
.\gradlew.bat build
```

## Links

[Modrinth](https://modrinth.com/project/customcape) · [Discord](https://discord.gg/YRzJZ2fdcC) · [Issues](https://github.com/sillisia/customcape/issues)

## License

MIT
