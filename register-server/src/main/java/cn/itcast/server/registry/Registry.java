package cn.itcast.server.registry;

import cn.itcast.server.pojo.ServiceInstance;

import java.util.HashMap;
import java.util.Map;

/**
 * 注册中心对象
 *  1. 帮助我们管理所有注册上来的服务
 *  2. 提供一个注册的方法
 *  3. 提供一个获取服务的方法
 */
public class Registry {
    //存储实例数据-最核心的数据 {"orderService":[{"id1", fuwu1},{"id2",fuwu2}]}
    private Map<String, Map<String, ServiceInstance>> registry = new HashMap<>();

    //构建线程安全的单例对象
    private Registry(){}


    /**
     * 静态的类和变量都是在类加载的时候进行初始化的
     * 而类加载的过程是线程安全的 所有就能够保证Registry能以线程安全的方式创建一个单例对象
     */
    private static class Singletion{
        private static Registry registry = new Registry();
    }

    public static Registry getInstance(){
        return Singletion.registry;
    }

    /**
     * 完成服务的注册
     * @param serviceInstance
     */
    public void register(ServiceInstance serviceInstance) {
        String serviceName = serviceInstance.getServiceName();
        String serviceInstanceId = serviceInstance.getServiceInstanceId();

        Map<String, ServiceInstance> map = this.registry.get(serviceInstance);
        if(null == map){
            //当前的服务是第一次注册
            map = new HashMap<>();
        }

        map.put(serviceInstanceId, serviceInstance);

        this.registry.put(serviceName, map);

        System.out.println(serviceName +  " : " + serviceInstanceId + " 完成注册");
    }

    /**
     * 根据服务的名称和id获取某一个服务
     * @param serviceName
     * @param serviceInstanceId
     * @return
     */
    public ServiceInstance getServiceInstance(String serviceName, String serviceInstanceId) {
        Map<String, ServiceInstance> map = this.registry.get(serviceName);
        if(null != map){
            ServiceInstance serviceInstance = map.get(serviceInstanceId);

            return serviceInstance;
        }

        return null;
    }
}
