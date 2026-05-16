# BluFi 插件使用教程

---

## 一、安装

```bash
npm install cordova-plugin-esp-blufi
npx cap sync android
```

---

## 二、Android 配置

在 `AndroidManifest.xml` 添加权限：

```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

---

## 三、调用方式

插件暴露一个全局对象 `Blufi`，使用方式如下：

### 3.1 注册事件监听

```javascript
document.addEventListener('deviceready', () => {
  Blufi.events(
    (event) => {
      // 事件处理
      console.log('事件:', event);
    },
    (error) => {
      console.error('错误:', error);
    }
  );
});
```

### 3.2 请求权限

```javascript
Blufi.requestPermissions(
  () => console.log('权限申请成功'),
  (err) => console.error('权限申请失败', err)
);
```

### 3.3 扫描设备

```javascript
// 开始扫描
Blufi.scan(
  () => console.log('扫描开始'),
  (err) => console.error('扫描失败', err)
);

// 停止扫描
Blufi.stopScan(
  () => console.log('扫描停止'),
  (err) => console.error('停止失败', err)
);
```

### 3.4 连接设备

```javascript
Blufi.connect(
  'AA:BB:CC:DD:EE:FF', // 设备蓝牙地址
  () => console.log('连接请求已发送'),
  (err) => console.error('连接失败', err)
);
```

### 3.5 安全协商

```javascript
Blufi.negotiateSecurity(
  () => console.log('安全协商请求已发送'),
  (err) => console.error('安全协商失败', err)
);
```

### 3.6 下发 Wi-Fi 配置

```javascript
Blufi.configureSta(
  'WiFi名称',      // SSID
  'WiFi密码',      // Password
  () => console.log('配置已下发'),
  (err) => console.error('配置失败', err)
);
```

### 3.7 查询设备状态

```javascript
Blufi.deviceStatus(
  () => console.log('状态查询已发送'),
  (err) => console.error('查询失败', err)
);
```

### 3.8 断开连接

```javascript
Blufi.disconnect(
  () => console.log('已断开'),
  (err) => console.error('断开失败', err)
);
```

---

## 四、调用流程

```
┌─────────────────────────────────────────────────────────┐
│                    完整配网流程                          │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
              ┌───────────────────────────┐
              │  1. deviceready 事件触发  │
              └───────────────────────────┘
                          │
                          ▼
              ┌───────────────────────────┐
              │  2. Blufi.events()        │
              │     注册事件监听            │
              └───────────────────────────┘
                          │
                          ▼
              ┌───────────────────────────┐
              │  3. Blufi.requestPermissions()
              │     请求权限               │
              └───────────────────────────┘
                          │
                          ▼
              ┌───────────────────────────┐
              │  4. Blufi.scan()          │
              │     开始扫描               │
              └───────────────────────────┘
                          │
                          ▼
              ┌───────────────────────────┐
              │  收到 'scan' 事件          │
              │  获取设备 name/address     │
              └───────────────────────────┘
                          │
                          ▼
              ┌───────────────────────────┐
              │  5. Blufi.stopScan()      │
              │     停止扫描               │
              └───────────────────────────┘
                          │
                          ▼
              ┌───────────────────────────┐
              │  6. Blufi.connect(address)│
              │     连接设备               │
              └───────────────────────────┘
                          │
                          ▼
              ┌───────────────────────────┐
              │  收到 'gattPrepared' 事件  │
              │  status=0 表示连接成功     │
              └───────────────────────────┘
                          │
                          ▼
              ┌───────────────────────────┐
              │  7. Blufi.negotiateSecurity()
              │     安全协商               │
              └───────────────────────────┘
                          │
                          ▼
              ┌───────────────────────────┐
              │  收到 'security' 事件       │
              │  status=0 表示协商成功     │
              └───────────────────────────┘
                          │
                          ▼
              ┌───────────────────────────┐
              │  8. Blufi.configureSta()   │
              │     下发 Wi-Fi 配置         │
              └───────────────────────────┘
                          │
                          ▼
              ┌───────────────────────────┐
              │  收到 'configure' 事件     │
              │  status=0 表示下发成功     │
              └───────────────────────────┘
                          │
                          ▼
              ┌───────────────────────────┐
              │  9. Blufi.deviceStatus()  │
              │     轮询设备状态           │
              └───────────────────────────┘
                          │
                          ▼
              ┌───────────────────────────┐
              │  收到 'deviceStatus' 事件   │
              │  staConnStatus=0 表示      │
              │  设备已连接 Wi-Fi          │
              └───────────────────────────┘
                          │
                          ▼
              ┌───────────────────────────┐
              │  10. Blufi.disconnect()    │
              │     配网完成，断开连接      │
              └───────────────────────────┘
```

---

## 五、事件详解

### 5.1 事件列表

| 事件类型 | 触发时机 | 包含数据 |
|---------|---------|---------|
| `scan` | 发现 BLE 设备 | name, address, rssi |
| `scanStarted` | 扫描开始 | - |
| `scanStopped` | 扫描结束 | - |
| `gattPrepared` | GATT 就绪 | status, address, mtu |
| `security` | 安全协商结果 | status |
| `configure` | 配置下发结果 | status |
| `deviceStatus` | 设备状态 | opMode, staSSID, staConnStatus |
| `disconnected` | 断开连接 | - |
| `permission` | 权限状态 | granted |

### 5.2 status 状态码

大多数事件返回 `status` 字段：

- `0` = 成功
- 其他值 = 失败（具体含义由设备端定义）

### 5.3 staConnStatus 设备连接状态

`deviceStatus` 事件中的 `staConnStatus`：

| 值 | 含义 |
|----|------|
| 0 | 设备已连接到目标 Wi-Fi |
| 1 | 连接失败 |
| 2 | 连接中 |
| 3 | 连接到其他 Wi-Fi |

---

## 六、完整示例

```javascript
// 等待设备就绪
document.addEventListener('deviceready', () => {
  console.log('设备就绪');

  // 1. 注册事件监听
  Blufi.events(handleEvent, handleError);

  // 2. 请求权限
  Blufi.requestPermissions(
    () => console.log('权限 OK'),
    (err) => console.error('权限失败', err)
  );

  // 3. 开始扫描
  Blufi.scan(
    () => console.log('扫描开始'),
    (err) => console.error('扫描失败', err)
  );
});

// 事件处理函数
function handleEvent(event) {
  const type = event.type;
  console.log('收到事件:', type, event);

  switch (type) {
    case 'scan':
      // 发现设备
      console.log(`设备: ${event.name} 地址: ${event.address}`);
      // 找到目标设备后停止扫描
      if (event.name && event.name.includes('BLUFI')) {
        Blufi.stopScan();
        // 连接设备
        Blufi.connect(event.address);
      }
      break;

    case 'scanStopped':
      console.log('扫描结束');
      break;

    case 'gattPrepared':
      // 连接成功
      if (event.payload && event.payload.status === 0) {
        console.log('连接成功，开始安全协商');
        Blufi.negotiateSecurity();
      }
      break;

    case 'security':
      // 安全协商成功
      if (event.payload && event.payload.status === 0) {
        console.log('安全协商成功，下发 Wi-Fi');
        Blufi.configureSta('WiFi名称', 'WiFi密码');
      }
      break;

    case 'configure':
      // 配置下发成功
      if (event.payload && event.payload.status === 0) {
        console.log('配置已下发，开始轮询状态');
        // 开始轮询设备状态
        pollStatus();
      }
      break;

    case 'deviceStatus':
      // 检查连接状态
      const connStatus = event.payload && event.payload.staConnStatus;
      if (connStatus === 0) {
        console.log('配网成功！');
        Blufi.disconnect();
      }
      break;

    case 'disconnected':
      console.log('设备已断开');
      break;
  }
}

function handleError(err) {
  console.error('BluFi 错误:', err);
}

// 轮询设备状态
function pollStatus() {
  const interval = setInterval(() => {
    Blufi.deviceStatus();
  }, 5000);

  // 最多轮询 30 秒
  setTimeout(() => {
    clearInterval(interval);
    console.log('轮询结束');
  }, 30000);
}
```

---

## 七、注意事项

1. **必须等待 `deviceready`** - Cordova 插件必须在 `deviceready` 事件后调用

2. **事件监听必须先注册** - 在调用其他方法前先调用 `Blufi.events()`

3. **权限是必须的** - Android 6.0+ 需要位置权限才能扫描 BLE

4. **数据通过事件返回** - 方法调用只发送命令，具体结果通过事件回调获取

5. **设备名称过滤** - 设备名称通常以 `BLUFI` 开头

6. **Wi-Fi 频段** - ESP8266/ESP32 只支持 2.4GHz
