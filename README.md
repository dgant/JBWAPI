[![Build Statues](https://github.com/JavaBWAPI/JBWAPI/actions/workflows/maven.yml/badge.svg)](https://github.com/JavaBWAPI/JBWAPI/actions/workflows/maven.yml)
[![CodeQL](https://github.com/JavaBWAPI/JBWAPI/actions/workflows/codeql.yml/badge.svg)](https://github.com/JavaBWAPI/JBWAPI/actions/workflows/codeql.yml)

# JBWAPI

Pure Java [bwapi](https://github.com/bwapi/bwapi) 4.4.0 client implementation backed by
[N00byEdge](https://github.com/N00byEdge)'s [JavaBWAPIBackend](https://github.com/N00byEdge/JavaBWAPIBackend) idea and
automated by [Bytekeeper](https://github.com/Bytekeeper).

Also contains a modified version of the pure Java BWEM implementation from [BWAPI4J](https://github.com/OpenBW/BWAPI4J).

## Goals

 - Have a similar (Java) interface to BWMirror to make porting BWMirror bots easy without all the DLL and JNI hassle and 
overhead.
 - Stay as updated as possible with the BWAPI releases.

## Advantages

 - No dependency on external DLL's.
 - At least [5x](https://github.com/JavaBWAPI/JBWAPI/issues/17) faster compared to BWMirror for primitives as it
directly reads the memory mapped client file. Even faster for BWAPI objects as it also avoids marshalling.
 - Supports both 32-and 64-bit Java (e.g. [deeplearning4j](https://deeplearning4j.org/) requires 64-bit Java which
BWMirror doesn't support).
 - BWEM instead of BWTA as map analyser.
 - Supports Linux "natively" using [openbw](https://github.com/JavaBWAPI/JBWAPI/pull/73), made possible by
[ByteKeeper](https://github.com/Bytekeeper).
 - `Async` support for realtime tournament constraints, made possible by [dgant](https://github.com/dgant).

## Warnings
 - A fake BWTA is provided for an easier transition from BWMirror. However, it translates BWTA calls to their respective 
BWEM calls, so specific Regions/Chokepoints etc. may differ.

## Usage

### Maven

Add JitPack as a repository:
```
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```
Add JBWAPI to your dependencies in `<dependencies></dependencies>`:
```
<dependency>
    <groupId>com.github.JavaBWAPI</groupId>
    <artifactId>JBWAPI</artifactId>
    <version>2.2.0</version>
</dependency>
```

### Gradle

Add JitPack as a repository:
```
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```
Add JBWAPI as a dependency:
```
dependencies {
    implementation 'com.github.JavaBWAPI:JBWAPI:2.2.0'
}
```

### Jar

Alternatively add the latest `.jar` from the [releases](https://github.com/JavaBWAPI/JBWAPI/releases) page to your
project.

### Faster startup

JBWAPI connects to BWAPI before initializing performance metrics or allocating
asynchronous frame buffers. This reduces work before connection readiness on all
supported platforms. No launch configuration changes are needed for these improvements.

#### One distribution for every supported platform

`mvn package` produces `target/jbwapi-2.2.0-distribution.zip`: the cross-platform
JAR with dependencies, plus `natives/` containing the matching Windows x86,
x86-64, and ARM64 DLLs and their licenses. Keep that directory beside the JAR when unpacking.
There is no operating-system or JVM-bitness variant to choose and no native-path
setting to configure.

On Windows, JBWAPI automatically selects the DLL for the running JVM's architecture,
regardless of StarCraft's bitness. It resolves `natives/` beside its JAR (or wrapped
EXE), independently of the launcher's working directory, and checks the DLL against
the bundled binary so stale files cannot supply a different JNA version. Other
platforms ignore the Windows files and use their own native-loading path.

A single bot JAR can still include JBWAPI and its dependencies. If the matching
Windows DLL is absent, outdated, or unreadable, or on another platform, JBWAPI
caches its bundled native library in a private directory under `jna.tmpdir`
(or `java.io.tmpdir`) and verifies it before reuse. Unavailable caches fall back to
JNA's normal loading. Explicit JNA loading settings take precedence, and an already
initialized JNA is left alone. `-Djbwapi.nativeCache=false` disables the cache while
still allowing bundled DLLs. Cached files can be deleted when bots are not running;
they will be recreated as needed.

#### Packaging your own bot

Include the Windows architectures supplied by your JNA dependency in the same
release, at `natives/com/sun/jna/<platform>/jnidispatch.dll` beside the bot's fat JAR
(`win32-x86`, `win32-x86-64`, and `win32-aarch64` with JNA 5.18.1).
JBWAPI selects the appropriate one automatically. Include the JNA license files
as well. Extract DLLs from the JNA dependency actually used in that bot so they
always match, including when overriding JBWAPI's JNA dependency.

For a Windows packaging check, add
`-Djna.nounpack=true`: the bundled DLL must load without runtime extraction.
An explicit `jna.boot.library.path` remains available for custom deployments.

## Compilation

`mvnw.cmd package`

or if you already have maven installed

`mvn package`

## Example

A simple `Hello World` bot is as easy as:

```java
import bwapi.*;

class HelloBot extends DefaultBWListener {
	private BWClient bwClient;

	@Override
	public void onFrame() {
		Game game = bwClient.getGame();
		game.drawTextScreen(100, 100, "Hello World!");
	}

	public static void main(String[] args) {
		HelloBot bot = new HelloBot();
		bot.bwClient = new BWClient(bot);
		bot.bwClient.startGame();
	}
}
```

## Documentation

The API documentation can be found [here](https://javabwapi.github.io/JBWAPI/).

You can also ask any further questions on the [SSCAIT Discord](https://discord.gg/DqvHsq9)

## Tutorial

If you are a just starting out with bot development, it might be helpful to follow the
[tutorial](https://github.com/JavaBWAPI/Java-BWAPI-Tutorial/wiki)!

## Bots

Some bots using [JBWAPI](https://github.com/JavaBWAPI/JBWAPI) (feel free to open a pull request to add yours!)

 - https://github.com/dgant/PurpleWave
 - https://github.com/Ravaelles/Atlantis
 - https://github.com/impie66/Kangaroo-Bot

## Linux

If you use Linux you can choose to develop normally and run the `jar` and `starcraft` using
[wine](https://www.winehq.org/) or try to use `openbw` by following the following
[instructions](./build_with_openbw.md).
