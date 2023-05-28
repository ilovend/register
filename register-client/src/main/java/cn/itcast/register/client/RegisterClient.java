package cn.itcast.register.client;

import cn.itcast.register.http.HttpSender;
import cn.itcast.register.pojo.HeartBeatRequest;
import cn.itcast.register.pojo.HeartBeatResponse;
import cn.itcast.register.pojo.RegisterRequest;
import cn.itcast.register.pojo.RegisterResponse;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 注册的客户端
 *  1. 启动线程完成注册和心跳
 */
public class RegisterClient {
    private RegisterClientWorker registerClientWorker;

    //线程是否运行的标识
    private Boolean isRuning = Boolean.TRUE;

    private Thread thread;

    public RegisterClient(){
        this.registerClientWorker = new RegisterClientWorker();
    }

    /**
     * 启动注册服务
     */

    public void start(){
        thread = new Thread(this.registerClientWorker);

        ThreadLocal threadLocal;

        thread.start();
    }

    /**
     * 线程优雅停机的方法
     */
    public void shutdown(){
        this.isRuning = Boolean.FALSE;

        //线程中断-可以让休眠中的线程直接结束
        thread.interrupt();
    }

    /**
     * 开启一个线程
     *  1. 先调用HttpSender 完成注册
     *  2. 然后调用HttpSender 完成续约 - 每个30秒一次
     *
     *  编写线程的时候推荐使用实现Runnable的方式来实现
     */
    private class RegisterClientWorker implements Runnable {

        private final Long HEART_BEAT_INTERVAL = 30L;

        private HttpSender httpSender;

        public RegisterClientWorker(){
            httpSender = new HttpSender();
        }

        public void run() {
            try{
                RegisterRequest request = new RegisterRequest();
                //这个属性正常应该是写到配置文件中
                request.setHostname("localhost");
                request.setIp("127.0.0.1");
                request.setPort(8090);
                request.setServiceName("orderService");
                request.setServiceInstanceId(UUID.randomUUID().toString());

                RegisterResponse response = httpSender.register(request);

                //判断注册是否成功 如果成功则发送心跳请求
                if(response.getStatus().equals(RegisterResponse.SUCCESS)){
                    while(isRuning){
                        HeartBeatRequest heartBeartRequest = new HeartBeatRequest();

                        heartBeartRequest.setServiceName(request.getServiceName());
                        heartBeartRequest.setServiceInstanceId(request.getServiceInstanceId());

                        HeartBeatResponse heartBeatResponse = httpSender.heartBeat(heartBeartRequest);

                        if(!heartBeatResponse.getStatus().equals(HeartBeatResponse.SUCCESS)){
                            System.out.println(request.getServiceName() + " " + request.getServiceInstanceId() + " 心跳续约失败");
                        }else{
                            System.out.println(request.getServiceName() + " " + request.getServiceInstanceId() + " 续约成功");
                        }

                        //休眠30秒
                        TimeUnit.SECONDS.sleep(HEART_BEAT_INTERVAL);
                    }

                }


            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

}
