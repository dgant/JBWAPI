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

A single bot JAR can still include JBWAPI and its dependencies for use on supported
platforms. JNA selects and extracts the matching native library at runtime; the
optional Windows packaging below avoids that extraction.

#### Optional Windows native libraries

On Windows, bots can also ship JNA's native DLL alongside their JAR to reduce the time
before the first connection attempt. This avoids extracting a temporary DLL on every
JVM launch, including Java 8's first-use `SecureRandom` initialization for temporary filenames.

`mvn package` produces `target/jbwapi-2.2.0-windows-natives.zip` in addition to the
existing JARs. The ZIP contains the Windows x86 and x86-64 `jnidispatch.dll` files
from the resolved JNA dependency, plus JNA's license files. Extract the ZIP into
your bot distribution, for example into a `natives` directory, and set
`jna.boot.library.path` before any JNA classes initialize:

```bat
java "-Djna.boot.library.path=C:\MyBot\natives\com\sun\jna\win32-x86" -jar MyBot.jar
```

Use `win32-x86` for a 32-bit JVM and `win32-x86-64` for a 64-bit JVM, regardless of
StarCraft's bitness. Keep the native DLLs matched to the JNA version used by the bot;
regenerate them when that dependency changes. If your bot overrides JBWAPI's JNA
dependency, extract the DLLs from that resolved JNA JAR instead.

For a packaging check, add `-Djna.nounpack=true` to fail if JNA would need to extract
a missing DLL. Without this option or a configured native directory, JNA retains
its normal loading and extraction behavior. Bundling these DLLs is optional and
does not require bundling a JRE or changing the StarCraft launch sequence.

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
