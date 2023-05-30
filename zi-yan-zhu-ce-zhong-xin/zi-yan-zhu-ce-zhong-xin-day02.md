# 自研注册中心-day02

## 1. 监控服务的监控状态

## 2. 监控线程的实现

```java
package cn.itcast.server.monitor;

import cn.itcast.server.data.Registry;
import cn.itcast.server.pojo.ServiceInstance;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ServiceAliveMonitor implements Runnable {

    private final Boolean RUNNING = Boolean.TRUE;
    public static Boolean IS_RUNNING = Boolean.FALSE;

    /**
     * 每个60秒检查一下服务是否掉线超过90秒 如果超过就删除服务实例
     */
    @Override
    public void run() {
        while(RUNNING){
            try {
                Registry registry = Registry.getInstance();
                Map<String, Map<String, ServiceInstance>> allServices = registry.getAllServices();

                Set<String> keys = allServices.keySet();
                for (String serviceName : keys) {
                    Map<String, ServiceInstance> instanceMap = allServices.get(serviceName);
                    Set<String> serviceIds = instanceMap.keySet();
                    for (String serviceId : serviceIds) {
                        ServiceInstance instance = instanceMap.get(serviceId);
                        if(!instance.isAlive()){
                            registry.moveService(serviceName, serviceId);
                        }
                    }

                }
                TimeUnit.SECONDS.sleep(60);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
```

Registry类添加如下方法:

```java
public Map<String, Map<String, ServiceInstance>> getAllServices(){
    return this.instances;
}

/**
     * 根据服务名称和服务id删除一个实例
     * @param serviceName
     * @param serviceId
     */
    public void moveService(String serviceName, String serviceId) {
        Map<String, ServiceInstance> instanceMap = instances.get(serviceName);

        instanceMap.remove(serviceId);

        System.out.println("服务 【" + serviceName +"】 掉线超过90秒被删除");

    }
```

类ServiceInstance添加如下方法:

```java
/**
 * 检查当前实例是否掉线90秒
 * @return
 */
public Boolean isAlive() {
    long currentTimeMillis = System.currentTimeMillis();
    if(currentTimeMillis - this.lease.lastHeartBeatTime > OFF_LINE_TIME){
        System.out.println(this.serviceName + " 服务掉线超过90秒了");
        return Boolean.FALSE;
    }

    System.out.println(this.serviceName + " 服务任然存活");
    return Boolean.TRUE;

}
```

## 4. 开启监控线程 并设置为Daemon守护线程

Registry类中修改中修改注册方法:

```java
/**
 * 将一个实例注册到注册中心
 * @param instance
 */
public void register(ServiceInstance instance){
    String serviceName = instance.getServiceName();
    Map<String, ServiceInstance> instanceMap = instances.get(serviceName);

    if(null == instanceMap){
        instanceMap = new HashMap<String, ServiceInstance>();
    }

    instanceMap.put(instance.getServiceInstanceId(), instance);
    instances.put(serviceName, instanceMap);

    System.out.println(instance.getServiceName() + " 注册成功了");
    System.out.println(instances);

    // 第一次添加实例的时候开启一次监控线程 并设置为守护线程
    if(!ServiceAliveMonitor.IS_RUNNING){
        ServiceAliveMonitor monitor = new ServiceAliveMonitor();
        Thread thread = new Thread(monitor);
        thread.setDaemon(true);
        thread.start();

        ServiceAliveMonitor.IS_RUNNING = Boolean.TRUE;
    }
}
```

## 4. 分离客户端的注册线程和心跳线程

```java
package cn.itcast.client;

import cn.itcast.heartbeat.HeartBeatRequest;
import cn.itcast.heartbeat.HeartBeatResponse;
import cn.itcast.http.HttpSender;
import cn.itcast.register.RegisterRequest;
import cn.itcast.register.RegisterResponse;
import lombok.SneakyThrows;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 进行服务的注册
 *  1. 需要通过线程的方式进行服务的注册
 *  2. 需要通过线程的方式进行服务的心跳
 */
public class RegisterClient {
    private String serviceInstanceId = null;
    private RegisterClientWorker clientWorker;
    private HeartBeatClientWorker heartBeatClientWorker;
    private Boolean finishedRegister;
    HttpSender sender = null;

    public static final String SERVICE_NAME = "orderservice";
    public static final String IP = "localhost";
    public static final String HOSTNAME = "admin";
    public static final int PORT = 9000;

    public RegisterClient(){
        this.serviceInstanceId = UUID.randomUUID().toString();
        this.finishedRegister = Boolean.FALSE;
        this.sender = new HttpSender();
        this.clientWorker = new RegisterClientWorker();
        this.heartBeatClientWorker = new HeartBeatClientWorker();
    }

    /**
     * 注册功能入口方法
     */
    @SneakyThrows
    public void start(){
        Thread thread = new Thread(clientWorker);
        thread.start();

        thread.join();

        Thread heartBeatThread = new Thread(heartBeatClientWorker);
        heartBeatThread.start();
    }

    /**
     * 心跳线程
     */
    private class HeartBeatClientWorker implements Runnable{

        public void run() {
            //发送心跳
            while(finishedRegister){
                try {
                    HeartBeatRequest heartBeatRequest = new HeartBeatRequest();
                    heartBeatRequest.setServiceInstanceId(serviceInstanceId);
                    heartBeatRequest.setServiceName(SERVICE_NAME);

                    HeartBeatResponse heartBeatResponse = sender.sendHeart(heartBeatRequest);
                    System.out.println(heartBeatResponse.getStatus() + "心跳结果");

                    TimeUnit.SECONDS.sleep(30);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 工作线程
     *  1. 调用发送组件发送注册服务
     *  2. 调用发送组件发送心跳请求
     */
    private class RegisterClientWorker implements Runnable {

        @SneakyThrows
        public void run() {
            // 发送注册请求
            RegisterRequest request = new RegisterRequest();
            //填充数据
            request.setHostname(HOSTNAME);
            request.setIp(IP);
            request.setPort(PORT);
            request.setServiceInstanceId(serviceInstanceId);
            request.setServiceName(SERVICE_NAME);

            RegisterResponse response = sender.send(request);
            //解析请求数据
            if(RegisterResponse.SUCCESS.equals(response.getStatus())){
                System.out.println(request.getServiceName() + " 注册成功");

                finishedRegister = Boolean.TRUE;
            }else{
                throw new RuntimeException("注册失败, 找不到注册中心");
            }
        }
    }
}
```

## 5. join方法实现线程插队

​ 注册线程和心跳线程抽取出来以后一定要保证注册线程先执行成功以后在发送心跳,此时就可以用join方法来实现线程的插队执行务必保证注册线程先执行成功以后在执行心跳线程。

```java
/**
 * 注册功能入口方法
 */
@SneakyThrows
public void start(){
    Thread thread = new Thread(clientWorker);
    thread.start();

    thread.join();

    heartBeatThread = new Thread(heartBeatClientWorker);
    heartBeatThread.start();
}
```

## 6. 使用interrupt方法关闭线程

​ 一般在开发中我们都会使用状态变量开实现线程的优雅停机,但是很多的线程可能会有休眠比如我们的心跳线程,可能你设置了停止的状态但是现在线程还处于休眠状态需要等待线程活过来以后才能读到最新的变量所以停机较慢，此时就需要使用interrupt方法来实现快速停机。

类RegisterClient中添加方法:

```java
/**
 * 实现线程的优雅停机
 */
public void shutdown() {
    this.finishedRegister = Boolean.FALSE;

    heartBeatThread.interrupt();
}
```

```java
public class TestDemo {
    public static void main(String[] args) throws InterruptedException {
        RegisterClient client = new RegisterClient();

        client.start();

        TimeUnit.SECONDS.sleep(5);

        client.shutdown();
    }
}
```

## 7. 客户端拉取注册中心数据并缓存在本地

```java
// 缓存数据的服务
public class CacheService {
    private final Long CACHE_FETCH_INTERVAL = 30 * 1000L;

    //客户端缓存的服务数据
    private Map<String, Map<String, ServiceInstance>> cacheService = new HashMap<String, Map<String, ServiceInstance>>();

    private RegisterClient registerClient;

    private Thread cacheThread;

    private HttpSender httpSender;

    public CacheService(RegisterClient registerClient, HttpSender httpSender){
        this.registerClient = registerClient;
        this.httpSender = httpSender;
    }

    public void init(){
        cacheThread = new Thread(new CacheThread());
        cacheThread.setDaemon(true);
        cacheThread.start();
    }

    public void shutdown(){
        cacheThread.interrupt();
    }


    private class CacheThread implements Runnable{

        @SneakyThrows
        public void run() {
            while(registerClient.finishedRegister){
                cacheService = httpSender.fetchService();

                TimeUnit.MICROSECONDS.sleep(CACHE_FETCH_INTERVAL);
            }
        }
    }

    public Map<String, Map<String, ServiceInstance>> getCacheService() {
        return cacheService;
    }
}
```

```java
/**
 * 发送http请求
 */
public class HttpSender {

    private final String  REGISTER_URL = "http://localhost:8080/server/register";
    private final String  HEARTBEAT_URL = "http://localhost:8080/server/heartbeat";
    private final String  FETCH_SERVICE_URL = "http://localhost:8080/server/getRegistry";

    public Map<String, Map<String, ServiceInstance>> fetchService() throws IOException {
        CloseableHttpClient client = HttpClients.createDefault();

        HttpGet httpGet = new HttpGet(FETCH_SERVICE_URL);
        CloseableHttpResponse response = client.execute(httpGet);

        HttpEntity entity = response.getEntity();
        String json = EntityUtils.toString(entity);
        Map map = JSON.parseObject(json, Map.class);

        System.out.println("缓存服务拉取到数据["+map+"]");

        return map;
    }
```

RegisterClient中修改代码:

```java
    private CacheService cacheService;
    public RegisterClient(){
		// .....
        this.cacheService = new CacheService(this, sender);
    }

    /**
     * 注册功能入口方法
     */
    @SneakyThrows
    public void start(){
        // .....
        cacheService.init();
    }

    /**
     * 实现线程的优雅停机
     */
    public void shutdown() {
        //....
        cacheService.shutdown();
    }
```

## 8. 数据读取的线程安全问题

​ 服务器端的服务数据会面临多线程读写的问题,那么以目前的代码来跑的肯定是不安全的,需要做安全的考虑,此处我们使用的是synchronized先临时的来解决一下,随着课程的深入我们会逐渐的优化。

```
 Registry 类里面所有的方法都加上synchronized
```

## 9. 注册中心服务保护的机制

​ 之前我们写过ServiceAliveMonitor监控如果90秒之内没有发送心跳,注册中心就会认为服务挂掉了，那么此时就会删除对应的服务,但此时其实还是有问题的-有没有可能是注册中心自己网络有问题导致其他所有的服务都注册不上呢？,所以如果90秒没有发送心跳就直接删除服务是不科学的，这可能会导致大量的服务被删除,所以应该有一个保护机制来解决该问题。

​ 服务保护机制: 注册中心会统计每分钟的所有心跳, 如果85%的心跳都有问题,那么应该是自己的网络等资源出现了问题而不是服务掉线此时不应该删除服务,如果出问题心跳小于85%那么才会被认定为是服务本身的问题，此时再删除服务。

​ 开发自我保护组件:

```java
package cn.itcast.server.protect;

import lombok.Data;

/**
 * 服务的自我保护器
 *  1. 统计每分钟的心跳次数,如果超过一分钟把次数清0
 *  2. 修改触发自我保护机制的阈值(服务数量 * 2 * 0.85), 如果有新服务注册或者服务掉线都需要修改该阈值
 *  3. 返回是否开启自我保护机制
 */
@Data
public class SelfProtector {

    //创建单例
    private static SelfProtector selfProtector = new SelfProtector();

    private Long heartBeatRateCount = 0L;

    //期望的心跳数量-比如有2个服务 期望的就是4个心跳
    private Long expectedHeartbeatRate = 0L;

    //期望的阈值 - 比如有2个服务 阈值就是 2 * 2 * 0.85 = 3.4
    private long expectedHeartbeatThreshold = 0L;

    //最近一分钟统计的开始时间
    private Long lastMinuteTime = System.currentTimeMillis();

    /**
     * 统计每分钟的心跳次数,如果超过一分钟把次数清0
     */
    public void increment(){
        Long current = System.currentTimeMillis();
        if(current - lastMinuteTime > 60 * 1000){
            heartBeatRateCount = 0L;
            lastMinuteTime = System.currentTimeMillis();
        }

        heartBeatRateCount ++;
    }

    /**
     *  修改触发自我保护机制的阈值(服务数量 * 2 * 0.85), 如果有新服务注册或者服务掉线都需要修改该阈值
     */
    public void updateCount(Long expectedHeartbeatRate, long expectedHeartbeatThreshold){
        this.expectedHeartbeatRate = expectedHeartbeatRate;
        this.expectedHeartbeatThreshold = expectedHeartbeatThreshold;
    }

    /**
     * 返回是否开启自我保护机制
     */

    public Boolean isProtected(){
        if(this.heartBeatRateCount < this.expectedHeartbeatThreshold){
            System.out.println("服务保护机制已经开启 [期望心跳"+this.expectedHeartbeatRate+ " 实际心跳: " + this.heartBeatRateCount +"]");
            return Boolean.TRUE;
        }

        System.out.println("服务保护机制未开启[期望心跳"+this.expectedHeartbeatRate+ " 实际心跳: " + this.heartBeatRateCount +"]");
        return Boolean.FALSE;
    }

    public static SelfProtector getInstance(){
        return selfProtector;
    }

}
```

更新期望心跳和阈值:

```java
1. 服务注册以后需要调用更新
public RegisterResponse register(@RequestBody RegisterRequest request){
    //.....
    //4. 更新自我保护机制的阈值
        SelfProtector selfProtector = SelfProtector.getInstance();
        selfProtector.updateCount(selfProtector.getExpectedHeartbeatRate() + 2, (long) (selfProtector.getExpectedHeartbeatRate() * 0.85));
}
2. 每次收到心跳更新收到的心跳数量
public HeartBeatResponse heartbeat(@RequestBody HeartBeatRequest request){
    //.....
    //4. 增加自我保护机制的心跳统计
        SelfProtector selfProtector = SelfProtector.getInstance();
        selfProtector.increment();
}
3. 监控线程删除服务后需要调用更新
for (ServiceInstance instance : instanceMap.values()) {
                        if(!instance.isAlive()){
                            registry.moveService(instance.getServiceName(), instance.getServiceInstanceId());
                            //移除实列需要更新自我保护的期望心跳和阈值
                            selfProtector.updateCount(selfProtector.getExpectedHeartbeatRate() -1,
                                    (long) (selfProtector.getExpectedHeartbeatRate() * 0.85));
                        }
                    }
```

出发自我保护机制:

```java
监控线程每次执行的时候都需要判断是否开启自我保护如果开启就不再删除服务
				//判断是否开启自我保护机制
                SelfProtector selfProtector = SelfProtector.getInstance();
                if(selfProtector.isProtected()){
                    TimeUnit.SECONDS.sleep(60);
                    continue;
                }
```

但是此时是不安全的需要给自我保护机制的方法添加上sync

```java
代码略
```

测试发现：实际心跳数量不会更新

```java
public SelfProtector(){
        CheckHeartCountThread checkHeartCountThread = new CheckHeartCountThread();
        Thread thread = new Thread(checkHeartCountThread);
        thread.setDaemon(true);
        thread.start();
    }
   
private class CheckHeartCountThread implements Runnable{

        @SneakyThrows
        @Override
        public void run() {
            while(true){
                synchronized (SelfProtector.class){
                    Long current = System.currentTimeMillis();
                    if(current - lastMinuteTime > 60 * 1000){
                        heartBeatRateCount = 0L;
                        lastMinuteTime = System.currentTimeMillis();
                    }

                    TimeUnit.SECONDS.sleep(1);
                }
            }

        }
    }
```

## 10. 顺序写

​ 目前所有地服务数据都是存储到内存中,如果重启服务数据就会丢失,此时我们要做好数据持久化地操作。数据持久化可以参考redis地持久化可以美注册一个服务就持久化一个服务,也可以定时对整个数据进行备份。

​ 此章节主要使用地是IO的顺序写来完成每个注册服务的持久化工作。

​ 为什么选择顺序写来完成数据的写入操作呢？主要是传统类型的io的性能太低了而注册服务可能会遇到服务大规模部署或者重启的压力所以必须提高IO的整体性能才可以。

​ 之前我们也学过MQ,都知道MQ的消息是需要持久化到本地文件中进行存储的,但是他们的读写性能是非常的高的,RocketMQ可以达到10W的QPS，Kafka可以达到100W的QPS，他们为什么能达到如此高的性能呢？就是因为他们使用了IO的顺序读写操作,我们此处的实现就是参考了RocketMQ的源码来实现的。

1. 导入json的jar包

```xml
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>fastjson</artifactId>
    <version>1.2.66</version>
</dependency>
```

2.  编写持久化类

    ```java
    package cn.itcast.server.io;

    import lombok.SneakyThrows;

    import java.io.FileNotFoundException;
    import java.io.IOException;
    import java.io.RandomAccessFile;
    import java.io.UnsupportedEncodingException;
    import java.nio.MappedByteBuffer;
    import java.nio.channels.FileChannel;

    /**
     * 使用顺序写完成数据的写错操作
     */
    public class PersistentService {
        private static final Long FILE_LENGTH = 1024 * 1024 * 1024L;
        private static PersistentService persistentService = new PersistentService();

        private Integer file_num = 1;
        private int position = 0;
        MappedByteBuffer byteBuffer = null;

        @SneakyThrows
        private PersistentService(){
            RandomAccessFile f = new RandomAccessFile("d://a" + file_num + ".json", "rw");
            FileChannel channel = f.getChannel();
            byteBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, FILE_LENGTH);
            file_num++;

        }

        public static PersistentService getInstance(){
            return persistentService;
        }

        public void writeService(String json){
            try {
                if(position >= FILE_LENGTH){
                    RandomAccessFile f = new RandomAccessFile("d://a" + file_num + ".json", "rw");
                    FileChannel channel = f.getChannel();
                    byteBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, FILE_LENGTH);
                    file_num++;
                }

                byteBuffer.position(position);
                byteBuffer.put((json + ",").getBytes("UTF-8"));

                position += json.length() + 1;

            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    ```
3. Registry调用持久化类

```java
/**
     * 将一个实例注册到注册中心
     * @param instance
     */
    public synchronized void register(ServiceInstance instance){
      	//.....
        // 完成数据的持久化操作
        PersistentService persistentService = PersistentService.getInstance();

        persistentService.writeService(JSON.toJSONString(instance));
    }
```
