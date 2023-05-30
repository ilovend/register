# 自研注册中心-day03

## 1. AtomicLong 原子类

​ 线程安全的原子类,主要是通过CAS来解决的线程安全问题。

```java
public class CASTest {

    public static void main(String[] args) {
        Counter counter = new Counter();

        for (int i=0; i< 1000; i++){
            new Thread(new Runnable() {
                @Override
                public void run() {
                    int value = counter.incrementAndGet();
                    System.out.println(value);
                }
            }).start();
        }
    }

    private static class Counter{
        private volatile int num = 0;

        public boolean compareAndSwap(int expectedValue, int newValue){
            synchronized (this){
                if(num == expectedValue){
                    num = newValue;
                    return true;
                }
               // System.out.println("num = " + num + ", newValue" + newValue);
                return false;
            }
        }

        public int incrementAndGet(){
            int value;
            int newValue;
            do {
                value = num;
                newValue = value + 1;
            }while (!compareAndSwap(value, newValue));

            return newValue;
        }
    }
}
```

​ cas会存在ABA的问题。

## 2. AtomicLong优化服务注册中心的心跳计数器

```java
老代码:
	//创建单例
    private static SelfProtector selfProtector = new SelfProtector();

    private Long heartBeatRateCount = 0L;
新代码:
    //创建单例
    private static SelfProtector selfProtector = new SelfProtector();

    //private Long heartBeatRateCount = 0L;
    private AtomicLong heartBeatRateCount = new AtomicLong(0);
解决报错的代码
```

## 3. LongAddr 优化高并发时AtomicLong 性能低的问题

```java
    //创建单例
    private static SelfProtector selfProtector = new SelfProtector();

    //private Long heartBeatRateCount = 0L;
    //private AtomicLong heartBeatRateCount = new AtomicLong(0);
    private LongAdder heartBeatRateCount = new LongAdder();
```

## 4. volatile优化线程可见性的问题

​ 如果一个线程一直读取一个变量的数据,另一个线程修改数据,此时就可能会出现线程可见性的问题,需要添加volatile关键字确保数据的可见性。

​ 同时需要注意sync具备volatile的所有的功能,所以如果一个方法或者代码块添加了sync那么他就不存在数据可见性的问题。

​ 在我们项目中典型的例子应该是客户端是否注册超过的状态，一个线程一直读取该状态且主线程关闭的时候需要修改该变量的状态就可能出现内存可见性的问题,需要添加volatile关键字来完成数据的同步。​

```java
public class TestVolatile {

    public static void main(String[] args) throws InterruptedException {

        Print print = new Print();

        for (int i=0; i<10; i++){
            new Thread(new Runnable() {
                @SneakyThrows
                @Override
                public void run() {
                    print.print();
                    TimeUnit.MILLISECONDS.sleep(10);
                }
            }).start();
        }


        TimeUnit.MILLISECONDS.sleep(100);

        print.updateFlag(false);

    }

    private static class Print{
        private boolean flag = true;

        public void updateFlag(boolean flag){
            this.flag = flag;
            System.out.println("1111");
        }

        public void print() throws InterruptedException {
            while(flag){
               
            }
        }
    }
}

```

```java
public class RegisterClient {
    private String serviceInstanceId = null;
    private RegisterClientWorker clientWorker;
    private HeartBeatClientWorker heartBeatClientWorker;
    public volatile Boolean finishedRegister; // 需要添加volatile关键字来完成数据的同步
    HttpSender sender = null;
    Thread heartBeatThread = null;
    private CacheService cacheService;
```

## 5. 服务的增量更新

​ 现在对于服务的更新我们采用的是30秒一次的全量更新, 如果服务的数据量比较少这也没什么问题，但是如果是服务数量比较的时候就会让我们的服务更新面临很大的压力而且很多时候服务基本没什么太大的变化,所以不应该每次都是使用全量更新,我们可以让服务启动的时候来一次全量更新,之前就采用更新更新的方式来完成服务的更新操作。

​ 要完成这个工作需要改造服务器端和客户端。

​ 服务器端需要先统计出更新的服务, 并提供全量更新和增量更新的接口。

​ 客户端需要编写2个线程来完成服务的增量更新和全量更新。

**先来完成服务器端的工作**:

```java
/**
 * 最近发生变化的服务
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecentlyChangeInstance {

    public static final String ADD_OPERATION = "insert";
    public static final String DEL_OPERATION = "del";

    //变更的服务
    private ServiceInstance serviceInstance;
    //服务变更的时间
    private Long lastChangeTime;
    //服务变更的类型(新增、删除)
    private String operation;
}
```

```java
public class Registry {
    //存储实例数据-最核心的数据
    private Map<String, Map<String, ServiceInstance>> instances = new HashMap<String, Map<String, ServiceInstance>>();

    //最近三分钟内变更的服务
    private LinkedList<RecentlyChangeInstance> recentlyChangeQueue = new LinkedList();

    private static final Long CHANGE_TIME_EXPIRED = 3 * 60 * 1000L;
    private static final Long CHANGE_INSTANCE_CHECK_INTERVEL = 3 * 1000L;
   
    public synchronized void register(ServiceInstance instance){
        //....
        //添加变更实列
        RecentlyChangeInstance changeInstance = new RecentlyChangeInstance(
                instance,
                System.currentTimeMillis(),
                RecentlyChangeInstance.ADD_OPERATION);

        this.recentlyChangeQueue.offer(changeInstance);
    }
    
    
    public synchronized void moveService(String serviceName, String serviceId){
        //....
        ServiceInstance instance = instanceMap.get(serviceId);
        //添加变更实列
        RecentlyChangeInstance changeInstance = new RecentlyChangeInstance(
                instance,
                System.currentTimeMillis(),
                RecentlyChangeInstance.DEL_OPERATION);

        this.recentlyChangeQueue.offer(changeInstance);
    }
    
```

```java
 /**
     * 监控修改队列 如果变更时间超过了3分钟就需要剔除
     */
    private class RecentlyChangeInstanceMonitor implements Runnable{

        @Override
        public void run() {
            while(true){
                try {
                    //锁定的是单列对象
                    synchronized (Singleton.registry) {
                        RecentlyChangeInstance instance = null;
                        while(null != (instance = recentlyChangeQueue.peek())){
                            Long curentTime = System.currentTimeMillis();
                            if(curentTime - instance.getLastChangeTime() > CHANGE_TIME_EXPIRED){
                                recentlyChangeQueue.pop();
                            }
                        }

                        TimeUnit.SECONDS.sleep(CHANGE_INSTANCE_CHECK_INTERVEL);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
```

启动监控线程

```java
//变更服务监控线程
    private Thread changeInstanceThread = null;
    private Registry(){
        changeInstanceThread = new Thread(new RecentlyChangeInstanceMonitor());
        changeInstanceThread.setDaemon(true);
        
        changeInstanceThread.start();
    }
```

服务器端提供拉取全量注册和增量注册的接口:

```java
	@GetMapping("/getRegistry")
    public Map<String, Map<String, ServiceInstance>> getFullRegistry(){
        return Registry.getInstance().getAllServices();
    }

    @GetMapping("/getDeltaRegistry")
    public LinkedList<RecentlyChangeInstance> getDeltaRegistry(){
        return Registry.getInstance().getDeltaService(); //注意方法需要添加sync
    }
```

**再来完成客户端的修改:**

​ 以前客户端只有一个线程拉取全量数据,线程要改造线程为2个一个拉取全量数据, 一个拉取增量数据。

```java
/**
     * 拉取全量数据
     */
    private class CacheFullThread implements Runnable{

        @SneakyThrows
        public void run() {
            cacheService = httpSender.fetchFullService();
        }
    }

    /**
     * 拉取增量数据
     */
    private class CacheDeltaThread implements Runnable{

        @SneakyThrows
        public void run() {
            while(registerClient.finishedRegister){

                TimeUnit.MICROSECONDS.sleep(CACHE_FETCH_INTERVAL);

                List<RecentlyChangeInstance> deltaService = httpSender.fetchDeltaService();

                //合并代码
                synchronized (cacheService){
                    mergeDelata(deltaService);
                }
            }
        }

        /**
         * 合并增量数据
         * @param deltaService
         */
        private void mergeDelata(List<RecentlyChangeInstance> deltaService) {
            if(null != deltaService && deltaService.size() > 0){
                for (RecentlyChangeInstance recentlyChangeInstance : deltaService) {
                    // 如果是新增
                    if(recentlyChangeInstance.getOperation().equals(RecentlyChangeInstance.ADD_OPERATION)){
                        // 从cacheMap中通过服务名称获取服务列表
                        Map<String, ServiceInstance> map = cacheService.get(recentlyChangeInstance.getServiceInstance().getServiceName());
                        // 可能当前这个变更服务是新增的 所以没有缓存数据
                        if(null == map){
                            map = new HashMap<String, ServiceInstance>();
                        }
                        // 无论是否有缓存数据 都直接存储到缓存map中即可
                        map.put(recentlyChangeInstance.getServiceInstance().getServiceInstanceId(), recentlyChangeInstance.getServiceInstance());
                    }else{
                        Map<String, ServiceInstance> map = cacheService.get(recentlyChangeInstance.getServiceInstance().getServiceName());
                        if(null != map){
                            map.remove(recentlyChangeInstance.getServiceInstance().getServiceInstanceId());
                        }
                    }
                }
            }
        }
```

HttpSender组件新增读取增量注册表的功能：

```java
private final String  FETCH_DELTA_SERVICE_URL = "http://localhost:8080/server/getDeltaRegistry";

    /**
     * 拉取增量数据
     * @return
     */
    public List<RecentlyChangeInstance> fetchDeltaService() throws IOException {
        CloseableHttpClient client = HttpClients.createDefault();

        HttpGet httpGet = new HttpGet(FETCH_SERVICE_URL);
        CloseableHttpResponse response = client.execute(httpGet);

        HttpEntity entity = response.getEntity();
        String json = EntityUtils.toString(entity);

        List<RecentlyChangeInstance> list = JSON.parseArray(json, RecentlyChangeInstance.class);

        return list;
    }
```

​ 此时还是可能会有问题，可能读取增量和合并增量注册表的时候会出现错误的情况,此时为了数据安全还应该增加数据纠错功能。

​ 可以让客户端拉取增量注册表的时候顺便拉取注册中心中的服务总数,然后客户端合并以后进行比对如果2侧的服务数据不对那么就需要触发纠错功能-全量拉取注册表。

客户端修改:

```java
//新增类
@Data
public class DelataRegistry {
    //变更服务列表
    private LinkedList<RecentlyChangeInstance> linkedList;
    //注册中心服务总数
    private Long totalCount;
}
	// 修改controller方法
@GetMapping("/getDeltaRegistry")
public DelataRegistry getDeltaRegistry(){
    return Registry.getInstance().getDeltaService();
}

// 修改getDeltaService方法
public synchronized DelataRegistry getDeltaService() {
        DelataRegistry delataRegistry = new DelataRegistry();

        Long totalCount = 0L;

        for (Map<String, ServiceInstance> map : instances.values()) {
            totalCount += map.size();
        }

        delataRegistry.setLinkedList(this.recentlyChangeQueue);
        delataRegistry.setTotalCount(totalCount);

        return delataRegistry;
    }
```

客户端修改:

```java
// 新增DelataRegistry类
// 修改HttSender
    /**
     * 拉取增量数据
     * @return
     */
    public DelataRegistry fetchDeltaService() throws IOException {
        CloseableHttpClient client = HttpClients.createDefault();

        HttpGet httpGet = new HttpGet(FETCH_SERVICE_URL);
        CloseableHttpResponse response = client.execute(httpGet);

        HttpEntity entity = response.getEntity();
        String json = EntityUtils.toString(entity);

        DelataRegistry delataRegistry = JSON.parseObject(json, DelataRegistry.class);

        return delataRegistry;
    }

// 修改 CacheDeltaThread类
    /**
     * 拉取增量数据
     */
    private class CacheDeltaThread implements Runnable{

        @SneakyThrows
        public void run() {
            while(registerClient.finishedRegister){

                TimeUnit.MICROSECONDS.sleep(CACHE_FETCH_INTERVAL);

                DelataRegistry delataRegistry = httpSender.fetchDeltaService();

                //合并代码
                synchronized (cacheService){
                    mergeDelata(delataRegistry.getLinkedList());
                }

                Long serverTotalCount = delataRegistry.getTotalCount();
                Long clientTotalCount = 0L;
                for (Map<String, ServiceInstance> map : cacheService.values()) {
                    clientTotalCount += map.size();
                }

                // 纠错如果两侧服务数量不相等则全量拉取一次服务
                if(serverTotalCount != clientTotalCount){
                    cacheService = httpSender.fetchFullService();
                }
            }
        }
```

## 6. 对象的原子性

​ 在我们的项目中会有增量拉取服务的时候,需要做全量替换的情况,如果第一个增量请求发出去并且请求到了数据但是返回的过程中卡住了, 此时第二个增量拉取请求又发出去了且先回来了数据,他就会把client中缓存的数据替换掉,最后第一个请求的结果回来了他又会把第二次请求的结果替换到，此时就会出现老数据替换新数据的情况，这种情况下就需要保证对象的原子性。

​ 什么是对象的原子性呢？

```java
public class ObjectCAS {
    private static AtomicReference<String> atomicReference = new AtomicReference<>("abc");

    public static void main(String[] args) throws InterruptedException {
        for (int i=0; i<100; i++){
            new Thread(new Runnable() {
                @Override
                public void run() {
                    atomicReference.compareAndSet("abc", "cba");
                }
            }).start();
        }


        for (int i=0; i<100; i++){
            new Thread(new Runnable() {
                @Override
                public void run() {
                    atomicReference.compareAndSet("cba", "abc");
                }
            }).start();
        }

        TimeUnit.MILLISECONDS.sleep(2000);
        System.out.println(atomicReference.get());
    }

}
```

服务器端修改:

```java
//新增类
/**
 * 用来包裹缓存注册中心
 */
@Data
public class ApplicationContext {
    private Map<String, Map<String, ServiceInstance>> cacheService = new HashMap<String, Map<String, ServiceInstance>>();
}

    @GetMapping("/getRegistry")
    public ApplicationContext getFullRegistry(){

        ApplicationContext context = new ApplicationContext();
        context.setCacheService(Registry.getInstance().getAllServices());

        return context;
    }
```

客户端修改:

```java
//新增类 ApplicationContext

	/**
	 * 全量拉取注册表的后台线程
	 * @author zhonghuashishan
	 *
	 */
	private class FetchFullRegistryWorker extends Thread {
		
		@Override
		public void run() {
			// 拉取全量注册表
			Applications fetchedApplications = httpSender.fetchFullRegistry();
			while(true) {
				Applications expectedApplications = applications.get();
				if(applications.compareAndSet(expectedApplications, fetchedApplications)) {
					break;
				}
			}
		}
		
	}
--------------------------------------------------------------------
                // 纠错如果两侧服务数量不相等则全量拉取一次服务
                if(serverTotalCount != clientTotalCount){

                    ApplicationContext oldContext = contextAtomicReference.get();
                    while(true){
                        ApplicationContext newContext = httpSender.fetchFullService();

                        System.out.println("纠错进行全量数据拉取 : " + newContext);
                        if(contextAtomicReference.compareAndSet(oldContext, newContext)){
                            break;
                        }
                    }
                }

//其他错误的代码修改正确
```

​ 但是此时这样实现仍会有ABA的问题, 当然这个ABA的概率机会为0，但是我们做中间件就应该考虑最极端的情况所以还需要优化一下。

## 7. AtomicStampedReference 优化对象的ABA问题

```java
    //private AtomicReference<ApplicationContext> contextAtomicReference = new AtomicReference<ApplicationContext>(new ApplicationContext());
    private AtomicStampedReference<ApplicationContext> contextAtomicStampedReference =
            new AtomicStampedReference<ApplicationContext>(new ApplicationContext(), 0);
```

```java
    /**
     * 拉取全量数据
     */
    private class CacheFullThread implements Runnable{

        @SneakyThrows
        public void run() {
            /*while (true){
                ApplicationContext oldContext = contextAtomicReference.get();

                ApplicationContext newContext = httpSender.fetchFullService();

                if(contextAtomicReference.compareAndSet(oldContext, newContext)){
                    break;
                }

                System.out.println("全量拉取服务成功 : " + newContext);
            }*/
            ApplicationContext newContext = httpSender.fetchFullService();
            while(true){
                ApplicationContext oldContext = contextAtomicStampedReference.getReference();
                int stamp = contextAtomicStampedReference.getStamp();

                if(contextAtomicStampedReference.compareAndSet(oldContext, newContext, stamp, stamp+1)){
                    break;
                }
            }
        }
    }
```

```java
                // 纠错如果两侧服务数量不相等则全量拉取一次服务
                if(serverTotalCount != clientTotalCount){

                    /*ApplicationContext oldContext = contextAtomicReference.get();
                    while(true){
                        ApplicationContext newContext = httpSender.fetchFullService();

                        System.out.println("纠错进行全量数据拉取 : " + newContext);
                        if(contextAtomicReference.compareAndSet(oldContext, newContext)){
                            break;
                        }
                    }*/
                    ApplicationContext newContext = httpSender.fetchFullService();
                    while(true){
                        ApplicationContext oldContxt = contextAtomicStampedReference.getReference();
                        int stamp = contextAtomicStampedReference.getStamp();
                        if(contextAtomicStampedReference.compareAndSet(oldContxt, newContext, stamp, stamp+1)){
                            break;
                        }
                    }

                }
            }
```

## 8. 确保注册中心版本不错乱

​ 之前讲到的内容可以保证对象在替换的时候替换的过程是线程安全的但是不能保证数据一定是正确的,所以还需要一个版本号来保证注册中心的数据的正确性。

```java
    /**
     * 拉取全量数据
     */
    private class CacheFullThread implements Runnable{

        @SneakyThrows
        public void run() {
            /*while (true){
                ApplicationContext oldContext = contextAtomicReference.get();

                ApplicationContext newContext = httpSender.fetchFullService();

                if(contextAtomicReference.compareAndSet(oldContext, newContext)){
                    break;
                }

                System.out.println("全量拉取服务成功 : " + newContext);
            }*/
            long version = applicationsVersion.get();

            if(applicationsVersion.compareAndSet(version, version+1)){
                ApplicationContext newContext = httpSender.fetchFullService();
                while(true){
                    ApplicationContext oldContext = contextAtomicStampedReference.getReference();
                    int stamp = contextAtomicStampedReference.getStamp();

                    if(contextAtomicStampedReference.compareAndSet(oldContext, newContext, stamp, stamp+1)){
                        break;
                    }
                }
            }
        }
    }
```

```java
DelataRegistry delataRegistry = httpSender.fetchDeltaService();
                System.out.println("增量拉取服务成功 : " + delataRegistry);

                long version = applicationsVersion.get();
                if(applicationsVersion.compareAndSet(version, version+1)){
                    //合并代码
                    synchronized (contextAtomicStampedReference){
                        mergeDelata(delataRegistry.getLinkedList());
                    }

                    Long serverTotalCount = delataRegistry.getTotalCount();
                    Long clientTotalCount = 0L;
                    for (Map<String, ServiceInstance> map : contextAtomicStampedReference.getReference().getCacheService().values()) {
                        clientTotalCount += map.size();
                    }

                    // 纠错如果两侧服务数量不相等则全量拉取一次服务
                    if(serverTotalCount != clientTotalCount){

                    /*ApplicationContext oldContext = contextAtomicReference.get();
                    while(true){
                        ApplicationContext newContext = httpSender.fetchFullService();

                        System.out.println("纠错进行全量数据拉取 : " + newContext);
                        if(contextAtomicReference.compareAndSet(oldContext, newContext)){
                            break;
                        }
                    }*/
                        ApplicationContext newContext = httpSender.fetchFullService();
                        while(true){
                            ApplicationContext oldContxt = contextAtomicStampedReference.getReference();
                            int stamp = contextAtomicStampedReference.getStamp();
                            if(contextAtomicStampedReference.compareAndSet(oldContxt, newContext, stamp, stamp+1)){
                                break;
                            }
                        }

                    }
                }
```
