# Random Stuff Client

A Fabric client with a searchable module menu, draggable category windows,
and Friends management. Client projects are organised by Minecraft version.

## Versions

| Minecraft | Client project | Download |
| --- | --- | --- |
| **1.21.11** | [Source, build files, and documentation](1.21.11/) | [random-stuff-client.jar](1.21.11/random-stuff-client.jar) |

## Install for Minecraft 1.21.11

1. Set up Minecraft **1.21.11** with Fabric Loader **0.19.5 or newer** and Java **21 or newer**.
2. Install Fabric API for Minecraft 1.21.11.
3. Download [`random-stuff-client.jar`](1.21.11/random-stuff-client.jar) and place it in your instance's `mods` folder, replacing older copies of the client or its separate GUI add-on.

## Menus

- `/randomstuff` opens the tabbed module menu with search and draggable category windows.
- `/friends` opens the Friends window to add, remove, and list friends.
- The **Classic** tab opens the original click GUI, including its Search panel.

The combined client uses Random Stuff Client display branding and includes the
lavender UI defaults. Internal mod IDs and existing settings remain compatible.

## Repository layout

```text
1.21.11/
├── README.md                 Version-specific instructions
├── src/main/java/            Available client GUI source
├── src/test/java/            Regression tests
├── libs/client-base.jar      Compiled underlying client
├── tools/                    Branding and package verification scripts
├── gradle/                   Gradle wrapper files
├── gradlew / gradlew.bat      Build launchers
├── build.gradle              Fabric Loom build and client merge
├── gradle.properties         Version and dependency settings
├── settings.gradle           Project and plugin configuration
└── random-stuff-client.jar   Combined installable client
```

## Build

From the repository root:

```sh
cd 1.21.11
./gradlew build
```

On Windows use `gradlew.bat build`. The combined JAR is generated at
`1.21.11/build/libs/random-stuff-client.jar`.

See the [1.21.11 README](1.21.11/README.md) for controls, verification commands,
and details of the available source. The full underlying client's source was
not provided; its compiled base is included alongside the editable GUI source.
In-game appearance and controls have not been verified in a running Minecraft instance.
