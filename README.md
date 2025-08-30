# Network Helper

[](https://www.minecraft.net)
[](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html)
[](https://www.mozilla.org/en-US/MPL/2.0/)

一个为 Minecraft Forge 模组开发者设计的，旨在简化网络数据包（Packet）处理的辅助模组。

## 简介

在 Forge 模组开发中，处理客户端与服务端之间的数据同步通常需要编写大量的网络数据包代码，这个过程繁琐且容易出错。`Network Helper` 的目标是大幅简化这一流程，让开发者可以更专注于功能逻辑本身。

它通过自动化扫描和注册、注解驱动配置以及一个创新的“懒人包”基类，将网络编程的复杂性降至最低。

## 主要特性

  * **自动化注册**: 无需手动注册任何网络包。本模组会在游戏加载时自动扫描所有已加载模组，并注册带有 `@PacketInfo` 注解的包类。
  * **注解驱动**: 只需一个简单的 `@PacketInfo` 注解，即可定义数据包的传输方向，使代码更清晰、更易于维护。
  * **两种实现方式**:
      * **标准接口 (`IPacket`)**: 为需要精细控制字节流的开发者提供传统实现方式。
      * **懒人包 (`EasyPacket`)**: 继承此类，只需定义字段，序列化和反序列化将由其全自动处理，极大地减少了样板代码。
  * **高性能**: `EasyPacket` 使用 `VarHandle` 来实现字段的自动读写，相比传统的反射，性能更高。
  * **简洁的 API**: 提供 `PacketManager.sendToServer`, `PacketManager.sendToPlayer` 等简单直观的静态方法，轻松发送数据包。

## 如何使用

### 步骤 1: 添加依赖

将 `Network Helper` 作为依赖项添加到你的 `build.gradle` 文件中。

```groovy
repositories {
    // ...
    flatDir { 
        dirs 'libs' 
    }
}

dependencies {
    // ...
    implementation fg.deobf("blank:networkhelper:0.1:beta")
}
```

将 networkhelper-0.1-beta.jar 文件放在 libs 目录下即可。

### 步骤 2: 创建你的数据包

你可以根据需求选择以下两种方式之一来创建数据包。

#### 方式 A: 继承 `EasyPacket` (推荐)

这是最简单的方式。只需定义字段并实现 `process()` 方法。支持多种常用数据类型（如 `int`, `String`, `ItemStack`, `CompoundTag` 等）的自动处理。

```java
@PacketInfo(direction = PacketDirection.CLIENT_TO_SERVER)
public class ExamplePacket2 extends EasyPacket {
    // 1. 定义需要传输的字段
    private int number;
    private String message;

    // 2. 提供发送时使用的构造函数
    public ExamplePacket2(int number, String message) {
        // ...
    }

    // 3. 提供解码时使用的构造函数 (必须)
    public ExamplePacket2(FriendlyByteBuf buf) {
        super(buf); // 自动读取所有字段
    }

    // 4. 实现核心逻辑
    @Override
    public void process(NetworkEvent.Context context) {
        // 此时，number 和 message 字段已被自动赋值，可直接使用
    }
}
```

#### 方式 B: 实现 `IPacket` 接口

如果需要完全手动控制数据的写入和读取，可以选择此方式。

```java
@PacketInfo(direction = PacketDirection.CLIENT_TO_SERVER)
public class ExamplePacket implements IPacket {
    private final int number;
    private final String message;

    // 发送时使用的构造函数
    public ExamplePacket(int number, String message) { /* ... */ }

    // 解码时使用的构造函数 (必须)
    public ExamplePacket(FriendlyByteBuf buf) { /* ... */ }

    // 手动实现编码逻辑
    @Override
    public void encode(FriendlyByteBuf buf) { /* ... */ }

    // 手动实现执行逻辑
    @Override
    public void execute(NetworkEvent.Context context) { /* ... */ }
}
```

### 步骤 3: 发送数据包

使用 `PacketManager` 来发送你的数据包实例。

```java
import com.mx_wj.networkHelper.network.core.PacketManager;

// 在某个客户端事件中
public class MyEventHandler {
    public static final String MOD_ID = "yourmodid"; // 替换为你的 Mod ID

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) {
            // 发送数据包到服务端
            PacketManager.sendToServer(MOD_ID, new ExamplePacket2(10, "Hello Server!"));
        }
    }
}
```

## 版本

目前版本仅支持Minecraft Forge 1.20.1 (逃

## 下载

可以去 Action 下到最新力作）

当然 Releases 也有，但是我可能更的不及时w

## 许可证

本模组使用 [Mozilla Public License 2.0](https://www.mozilla.org/en-US/MPL/2.0/) 许可证。

## 作者

mx_wj