# Minecraft Forge Installer Installer
[![Build Status](https://dev.me1312.net/jenkins/job/Forge%20Installer%20Installer/badge/icon)](https://dev.me1312.net/jenkins/job/Forge%20Installer%20Installer/) <br><br>
The Minecraft Forge Installers have a problem &mdash; and that is the simple fact that cloud resources can change over time. But that's okay, because that's what HTTP 300-series redirects are for.<br>

The only problem with that, is that the installers don't follow 300-series redirects. And so, the need for an Installer for the Installer arised. Minecraft Forge Installer Installer attempts to repair potentially broken Minecraft Forge Installers.

### Downloads
> [https://dev.me1312.net/jenkins/job/Forge Installer Installer](https://dev.me1312.net/jenkins/job/Forge%20Installer%20Installer)<br>
> [https://files.minecraftforge.net](https://files.minecraftforge.net)

## Installation Instructions
```
java -jar forge-installer-installer.jar forge-<numbers>-installer.jar
```
The first step is to run Minecraft Forge Installer Installer. It takes the path to the real installer as its only argument.

The Installer Installer does a few things, but the most important one is that it determines what the actual URLs of these resources are by simply following the redirects and printing them to the screen. If you see more than a couple status codes that aren't in the 200-series or 300-series, then you're probably SOL.

After it's done, you can then run your freshly updated copy of Minecraft Forge Installer.
<br>
