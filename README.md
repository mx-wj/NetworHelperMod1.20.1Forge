# NetworkHelperMod 1.20.1 Forge

一个Minecraft Forge 1.20.1的网络注册Mod，用于简化网络数据包的注册和处理。

## 功能特点

- 提供便捷的网络数据包注册系统
- 支持自动序列化/反序列化数据包
- 包含事件订阅系统
- 提供ASM动态类加载功能

## 使用方法

### 数据包定义

创建数据包类时需继承`EasyPacket`或实现`IPacket`接口，并使用`@PacketInfo`注解指定数据包方向。该模组会帮您自动注册网络包

```java
@PacketInfo(direction = PacketDirection.CLIENT_TO_SERVER)
public class ExamplePacket implements IPacket {
    // 数据包实现
}
```

### 数据包注册

PacketManager用于管理数据包的注册和发送：

```java
PacketManager.registerPackets();
PacketManager.sendToServer(packet);
PacketManager.sendToPlayer(player, packet);
PacketManager.sendToAllPlayers(packet);
```

## API说明

### PacketDirection
数据包方向枚举：
- CLIENT_TO_SERVER
- SERVER_TO_CLIENT

### PacketManager
核心数据包管理类，提供：
- 自动数据包注册
- 数据包编码/解码
- 数据包发送方法

## 开发者指南

### 添加自定义数据包
1. 创建继承`EasyPacket`的类或实现`IPacket`接口的类
2. 使用`@PacketInfo`注解指定数据包方向

### 数据包处理
实现`IPacket`接口需要提供：
- `encode(FriendlyByteBuf buf)` - 数据包序列化方法
- `execute(NetworkEvent.Context context)` - 数据包执行方法

## 贡献指南
欢迎贡献代码和改进。
- 测试新增功能
- 提交清晰的提交信息

## 许可证
本项目采用MPL 2.0许可证。详情请查看LICENSE.txt文件。