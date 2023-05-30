# 自研注册中心-day01

## 1. 为什么学习这个课程？

​ 很多人学了并发编程一大堆的概念，不知道怎么用，同时大家在黑马培训还是主要以业务功能为主,不会牵涉到太多并发相关的内容,但是现在面试又问题的特别的多,且不再单纯的问并发的概率、api等，更多的是问你们在开发的过程中是否有使用到并发？ 如果使用到了那么是在什么业务场景中使用的？怎么样的？基本都问这些内容了，所以我们要通过一个课程来深度的学习并发在实际项目中如何使用。

## 2. 本次课程开发什么？

​ 主要是根据Eureka注册中心的源码开发一套属于自己的注册中心。主要用到的知识点如下:

```
	1. 线程的创建
		2. 线程的优雅停机
  		3. 线程的中断-interrupt方法
    		4. 线程插队-join方法
      		5. sync关键字
        		6.  volatile关键字
          		7. Atomic原子类
            		8. LongAddr
              		9.  ConcurrentHashMap
                		10. AtomicReference类
                  		11. AtomicStampedReference类
                    		12. 线程池
                      		13. wait和notify方法
                        		14. CountDownLatch类
                          		15. CyclicBarrier类
```

## 3. 工程的创建

register-server： 作为注册中心的服务器端

register-client：作为注册中心的客户端, 他将被打成一个普通的jar包被应用服务所使用

## 4. 客户端组件化设计

​ 优秀的项目都是经过良好设计的，而不是一上来就开始胡乱的编写代码, 我们的注册中心也是根据组件化的思想对各个功能模块进行组件化的设计。

## 5. 开发客户端注册和心跳功能

```java
package cn.itcast.client;

import cn.itcast.worker.RegisterClientWorker;

import java.util.UUID;

/**
 * 进行服务的注册
 *  1. 需要通过线程的方式进行服务的注册
 *  2. 需要通过线程的方式进行服务的心跳
 */
public class RegisterClient {
    private String serviceInstanceId = null;
    private RegisterClientWorker clientWorker;

    public RegisterClient(){
        this.serviceInstanceId = UUID.randomUUID().toString();
        this.clientWorker = new RegisterClientWorker(this.serviceInstanceId);
    }

    /**
     * 注册功能入口方法
     */
    public void start(){
        Thread thread = new Thread(clientWorker);
        thread.start();
    }
}
```

```java
package cn.itcast.worker;

import cn.itcast.heartbeat.HeartBeatRequest;
import cn.itcast.heartbeat.HeartBeatResponse;
import cn.itcast.http.HttpSender;
import cn.itcast.register.RegisterRequest;
import cn.itcast.register.RegisterResponse;

import java.util.concurrent.TimeUnit;

/**
 * 工作线程
 *  1. 调用发送组件发送注册服务
 *  2. 调用发送组件发送心跳请求
 */
public class RegisterClientWorker implements Runnable {

    private String serviceInstanceId = null;

    private Boolean finishedRegister;

    public RegisterClientWorker(String serviceInstanceId) {
        this.serviceInstanceId = serviceInstanceId;
        this.finishedRegister = Boolean.FALSE;
    }

    public void run() {
        HttpSender sender = new HttpSender();

        // 发送注册请求
        RegisterRequest request = new RegisterRequest();
        //填充数据
        request.setHostname("localhost");
        request.setIp("127.0.0.1");
        request.setPort(8808);
        request.setServiceInstanceId(this.serviceInstanceId);
        request.setServiceName("ordeservice");

        RegisterResponse response = sender.send(request);
        //解析请求数据
        if(RegisterResponse.SUCCESS.equals(response.getStatus())){
            System.out.println(request.getServiceName() + " 注册成功");

            finishedRegister = Boolean.TRUE;
        }else{
            throw new RuntimeException("注册失败, 找不到注册中心");
        }

        //发送心跳
        while(finishedRegister){
            try {
                HeartBeatRequest heartBeatRequest = new HeartBeatRequest();
                heartBeatRequest.setServiceInstanceId(this.serviceInstanceId);

                HeartBeatResponse heartBeatResponse = sender.sendHeart(heartBeatRequest);
                System.out.println(heartBeatResponse.getStatus() + "心跳结果");

                TimeUnit.SECONDS.sleep(30);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }
}

```

```java
package cn.itcast.http;

import cn.itcast.heartbeat.HeartBeatRequest;
import cn.itcast.heartbeat.HeartBeatResponse;
import cn.itcast.register.RegisterRequest;
import cn.itcast.register.RegisterResponse;

/**
 * 发送http请求
 */
public class HttpSender {

    /**
     * 发送注册请求
     * @param request
     * @return
     */
    public RegisterResponse send(RegisterRequest request) {
        System.out.println(request.getServiceName() + "发送注册请求成功");

        RegisterResponse response = new RegisterResponse();

        response.setMsg("注册成功");
        response.setStatus(RegisterResponse.SUCCESS);

        return response;
    }

    public HeartBeatResponse sendHeart(HeartBeatRequest heartBeatRequest) {

        System.out.println(heartBeatRequest.getServiceInstanceId() + " 续约成功");

        HeartBeatResponse response = new HeartBeatResponse();
        response.setStatus(HeartBeatResponse.SUCCESS);
        response.setMsg("续约成功");

        return response;
    }
}
```

```java
package cn.itcast.register;

import lombok.Data;

import java.io.Serializable;

/**
 * 注册请求
 */
@Data
public class RegisterRequest implements Serializable {

    private String serviceName;
    private String ip;
    private Integer port;
    private String serviceInstanceId;
    /**
     * 服务所在机器的主机名
     */
    private String hostname;

}
```

```java
package cn.itcast.register;

import lombok.Data;

/**
 * 注册响应
 */
@Data
public class RegisterResponse {

    public static final Integer SUCCESS = 200;
    public static final Integer FAIL = 500;

    private Integer status;

    private String msg;
}
```

```java
package cn.itcast.heartbeat;

import lombok.Data;

/**
 * 心跳的请求
 */
@Data
public class HeartBeatRequest {
    private String serviceInstanceId;
}
```

```java
package cn.itcast.heartbeat;

import lombok.Data;

@Data
public class HeartBeatResponse {

    public static final Integer SUCCESS = 200;
    public static final Integer FAIL = 500;

    private Integer status;

    private String msg;
}
```

## 6. 代码优化

​ 删除RegisterClientWorker类并把他的代码写到RegisterClient中,因为这个线程属于client的线程,很多开源项目都是使用内部类来封装到一个类中。

```java
package cn.itcast.client;

import cn.itcast.heartbeat.HeartBeatRequest;
import cn.itcast.heartbeat.HeartBeatResponse;
import cn.itcast.http.HttpSender;
import cn.itcast.register.RegisterRequest;
import cn.itcast.register.RegisterResponse;

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

    public RegisterClient(){
        this.serviceInstanceId = UUID.randomUUID().toString();
        this.clientWorker = new RegisterClientWorker(this.serviceInstanceId);
    }

    /**
     * 注册功能入口方法
     */
    public void start(){
        Thread thread = new Thread(clientWorker);
        thread.start();
    }


    /**
     * 工作线程
     *  1. 调用发送组件发送注册服务
     *  2. 调用发送组件发送心跳请求
     */
    private class RegisterClientWorker implements Runnable {

        private String serviceInstanceId = null;

        private Boolean finishedRegister;

        public RegisterClientWorker(String serviceInstanceId) {
            this.serviceInstanceId = serviceInstanceId;
            this.finishedRegister = Boolean.FALSE;
        }

        public void run() {
            HttpSender sender = new HttpSender();

            // 发送注册请求
            RegisterRequest request = new RegisterRequest();
            //填充数据
            request.setHostname("localhost");
            request.setIp("127.0.0.1");
            request.setPort(8808);
            request.setServiceInstanceId(this.serviceInstanceId);
            request.setServiceName("ordeservice");

            RegisterResponse response = sender.send(request);
            //解析请求数据
            if(RegisterResponse.SUCCESS.equals(response.getStatus())){
                System.out.println(request.getServiceName() + " 注册成功");

                finishedRegister = Boolean.TRUE;
            }else{
                throw new RuntimeException("注册失败, 找不到注册中心");
            }

            //发送心跳
            while(finishedRegister){
                try {
                    HeartBeatRequest heartBeatRequest = new HeartBeatRequest();
                    heartBeatRequest.setServiceInstanceId(this.serviceInstanceId);

                    HeartBeatResponse heartBeatResponse = sender.sendHeart(heartBeatRequest);
                    System.out.println(heartBeatResponse.getStatus() + "心跳结果");

                    TimeUnit.SECONDS.sleep(30);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        }
    }
}
```

## 7. 注册中心服务器端注册功能实现思路

```java
package cn.itcast.server.controller;

import cn.itcast.server.data.Registry;
import cn.itcast.server.heartbeat.HeartBeatRequest;
import cn.itcast.server.heartbeat.HeartBeatResponse;
import cn.itcast.server.pojo.Lease;
import cn.itcast.server.pojo.ServiceInstance;
import cn.itcast.server.register.RegisterRequest;
import cn.itcast.server.register.RegisterResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 负责接受客户端的请求
 *  1. 注册请求
 *  2. 心跳请求
 */
public class RegisterServerController {

    /**
     * 服务注册
     * @param request
     * @return
     */
    public RegisterResponse register(@RequestBody RegisterRequest request){
        //1. 创建实列
        ServiceInstance serviceInstance = new ServiceInstance();
        serviceInstance.setHost(request.getHostname());
        serviceInstance.setIp(request.getIp());
        serviceInstance.setPort(request.getPort());
        serviceInstance.setServiceInstanceId(request.getServiceInstanceId());
        serviceInstance.setServiceName(request.getServiceName());
        serviceInstance.setLease(new Lease());

        //2. 获取单例的Register对象
        Registry registry = Registry.getInstance();


        //3. 添加一个实例到注册中心
        registry.register(serviceInstance);

        RegisterResponse response = new RegisterResponse();
        response.setMsg("注册成功");
        response.setStatus(RegisterResponse.SUCCESS);
        return response;
    }

    /**
     * 处理心跳信息
     */
    public HeartBeatResponse heartbeat(HeartBeatRequest request){
        //1. 获取服务名称和服务id
        String serviceInstanceId = request.getServiceInstanceId();
        String serviceName = request.getServiceName();
        //2. 获取Registry对象
        Registry registry = Registry.getInstance();
        //3. 获取续约的实例对象
        ServiceInstance instance = registry.getServiceInstance(serviceName, serviceInstanceId);

        if(null != instance){
            // 续约
            instance.getLease().renew();
            System.out.println(instance.getServiceName() + " 续约成功");
        }

        HeartBeatResponse response = new HeartBeatResponse();
        response.setMsg("续约成功");
        response.setStatus(HeartBeatResponse.SUCCESS);

        return response;
    }

}
```

```java
package cn.itcast.server.data;

import cn.itcast.server.pojo.ServiceInstance;

import java.util.HashMap;
import java.util.Map;

/**
 * 注册中心的核心对象 用来存储数据
 *  1. 因为数据只需要一份所以该类是单例
 *  2. 需要提供服务注册的方法
 *  3. 需要提供获取服务的方法
 */
public class Registry {
    //存储实例数据-最核心的数据
    private Map<String, Map<String, ServiceInstance>> instances = new HashMap<String, Map<String, ServiceInstance>>();

    private Registry(){}

    /**
     * 通过服务名称和服务id获取注册中心中的某一个实列
     * @param serviceName
     * @param serviceInstanceId
     * @return
     */
    public ServiceInstance getServiceInstance(String serviceName, String serviceInstanceId) {
        Map<String, ServiceInstance> instanceMap = instances.get(serviceName);
        if(null != instanceMap){
            ServiceInstance instance = instanceMap.get(serviceInstanceId);
            return instance;
        }

        return null;
    }

    private static class Singleton{
        private static Registry registry = new Registry();
    }

    public static Registry getInstance(){
        return Singleton.registry;
    }

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

    }
}
```

```java
package cn.itcast.server.pojo;

import lombok.Data;

@Data
public class ServiceInstance {
    private String serviceName;
    private Integer port;
    private String ip;
    private String host;
    private String serviceInstanceId;

    private Lease lease;
}
```

```java
package cn.itcast.server.pojo;

/**
 * 契约
 *
 */
public class Lease {
    private Long lastHeartBeatTime = System.currentTimeMillis();

    public void renew(){
        this.lastHeartBeatTime = System.currentTimeMillis();
    }
}
```

## 8. 契约的改造

​ 契约应该是属于一个实例的 所以应该定义在实例对象中形成一个内部类。

```java
package cn.itcast.server.pojo;

import lombok.Data;

@Data
public class ServiceInstance {
    private String serviceName;
    private Integer port;
    private String ip;
    private String host;
   	private String serviceInstanceId;
    private Lease lease;
    /**
     * 契约
     *
     */
    private class Lease {
        private Long lastHeartBeatTime = System.currentTimeMillis();

        public void renew(){
            this.lastHeartBeatTime = System.currentTimeMillis();
            System.out.println("服务实例【" + serviceInstanceId + "】，进行续约：" + lastHeartBeatTime);
        }
    }
}
```

## 9. 改造Server和client

​ 把server做成一个springboot的服务

​ client引入httpclient发送请求

server导入如下jar包:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

server添加启动引导类:

```java
package cn.itcast.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RegisterServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(RegisterServerApplication.class);
    }
}
```

server的controller添加springmvc的注解。

client添加如下jar包:

```xml
<!-- https://mvnrepository.com/artifact/org.apache.httpcomponents/httpcore -->
<dependency>
    <groupId>org.apache.httpcomponents</groupId>
    <artifactId>httpcore</artifactId>
    <version>4.4.10</version>
</dependency>

<!-- https://mvnrepository.com/artifact/org.apache.httpcomponents/httpclient -->
<dependency>
    <groupId>org.apache.httpcomponents</groupId>
    <artifactId>httpclient</artifactId>
    <version>4.5.6</version>
</dependency>
```

修改HttpSender类发送真正的请求请求controller：

```java
package cn.itcast.http;

import cn.itcast.heartbeat.HeartBeatRequest;
import cn.itcast.heartbeat.HeartBeatResponse;
import cn.itcast.register.RegisterRequest;
import cn.itcast.register.RegisterResponse;
import com.alibaba.fastjson.JSON;
import com.sun.org.apache.bcel.internal.generic.NEW;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;

/**
 * 发送http请求
 */
public class HttpSender {

    private final String  REGISTER_URL = "http://localhost:8080/server/register";
    private final String  HEARTBEAT_URL = "http://localhost:8080/server/heartbeat";

    /**
     * 发送注册请求
     * @param request
     * @return
     */
    public RegisterResponse send(RegisterRequest request) throws IOException {
        
        //1. 构建HttpClient对象
        CloseableHttpClient client = HttpClients.createDefault();

        //2. 准备参数
        HttpPost post = new HttpPost(REGISTER_URL);
        StringEntity entity = new StringEntity(JSON.toJSONString(request), ContentType.APPLICATION_JSON);
        entity.setContentEncoding("utf-8");

        post.setEntity(entity);

        //3. 发送请求
        CloseableHttpResponse response = client.execute(post);

        //4. 解析结果
        HttpEntity responseEntity = response.getEntity();
        String responseJson = EntityUtils.toString(responseEntity);

        RegisterResponse resp = JSON.parseObject(responseJson, RegisterResponse.class);

        System.out.println(request.getServiceName() + "发送注册请求成功");

        return resp;
    }

    public HeartBeatResponse sendHeart(HeartBeatRequest heartBeatRequest) throws IOException {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost post = new HttpPost(HEARTBEAT_URL);
        StringEntity entity = new StringEntity(JSON.toJSONString(heartBeatRequest), ContentType.APPLICATION_JSON);
        entity.setContentEncoding("utf-8");

        post.setEntity(entity);

        CloseableHttpResponse response = client.execute(post);
        HttpEntity responseEntity = response.getEntity();
        String responseJson = EntityUtils.toString(responseEntity);


        HeartBeatResponse resp = JSON.parseObject(responseJson, HeartBeatResponse.class);

        System.out.println(heartBeatRequest.getServiceInstanceId() + " 续约成功");
        return resp;
    }
}
```
