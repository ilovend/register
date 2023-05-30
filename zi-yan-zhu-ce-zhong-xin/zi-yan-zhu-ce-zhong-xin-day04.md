# 自研注册中心-day04

## 1. 读多写少场景锁优化

​ 目前我们对于Registry注册中心中的所有的方法都是添加的sync，全部都做成了单线程的,效率肯定还是很低的,同时我们也做过微服务的项目了都知道服务的注册应该是远远低于服务的读取的，所以没有必要全部做成多线程的,此时可以用读写锁来优化这一段代码。

```java
	/**
	 * 服务注册表的锁
	 */
	private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	private ReadLock readLock = lock.readLock();
	private WriteLock writeLock = lock.writeLock();

然后给各个场景下的业务添加读锁或者写锁
```

## 2. cyclicbarrier实现数据恢复

```java
/**
 * 数据的恢复
 */
public class RecoverService implements Runnable {
    @Override
    public void run() {
        File file = new File("d://");
        File[] files = file.listFiles();
        int fileNum = 0;
        for (File f : files) {
            if(f.isFile() && f.getName().contains(".json")){
                fileNum++;
            }
        }


        List<String> list = new ArrayList<>(fileNum);
        Map<String, Map<String, ServiceInstance>> instances = new HashMap<String, Map<String, ServiceInstance>>();

        CyclicBarrier cyclicBarrier = new CyclicBarrier(fileNum-1, new Runnable() {
            @Override
            public void run() {
                System.out.println("文件读取完成开始汇总结果数据");
                if(null != list && list.size() > 0){
                    for (String json : list) {
                        String[] jsons = json.split(">");
                        for (String jsonStr : jsons) {
                            if(null != jsonStr && jsonStr.contains("serviceInstanceId")){
                                ServiceInstance serviceInstance = JSON.parseObject(jsonStr, ServiceInstance.class);
                                Map<String, ServiceInstance> map = instances.get(serviceInstance.getServiceName());
                                if(null == map){
                                    map = new HashMap<>();
                                }
                                map.put(serviceInstance.getServiceInstanceId(), serviceInstance);
                                instances.put(serviceInstance.getServiceName(), map);
                            }

                        }
                    }
                }

                System.out.println("恢复数据为： " + instances);

                Registry registry = Registry.getInstance();
                registry.setAllService(instances);
            }
        });


        for (int i=1 ; i< fileNum ;i++){
            int num = i;
            int finalFileNum = fileNum;
            new Thread(new Runnable() {
                @SneakyThrows
                @Override
                public void run() {
                    List<String> jsonList = new ArrayList<>();
                    RandomAccessFile f = new RandomAccessFile("d://a" + num + ".json", "rw");
                    FileChannel channel = f.getChannel();
                    MappedByteBuffer byteBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, PersistentService.FILE_LENGTH);

                    System.out.println("读取文件d://a"+ num + ".json");
                    if(num != finalFileNum){
                        byte[] bytes = new byte[Math.toIntExact(PersistentService.FILE_LENGTH)];
                        byteBuffer.get(bytes,0, Math.toIntExact(PersistentService.FILE_LENGTH));

                        list.add(num-1,new String(bytes));
                    }else{
                        byte[] bytes = new byte[PersistentService.getInstance().position];
                        byteBuffer.get(bytes,0, PersistentService.getInstance().position);

                        list.add(num-1,new String(bytes));
                    }

                    cyclicBarrier.await();
                }
            }).start();
        }
    }
}

```

## 3. 线程池优化读取线程

```
ExecutorService executorService = Executors.newCachedThreadPool();
        for (int i=1 ; i< fileNum ;i++){
            int finalFileNum = fileNum;
            int num = i;
            executorService.submit(new Runnable() {
                @SneakyThrows
                @Override
                public void run() {
                    List<String> jsonList = new ArrayList<>();
                    RandomAccessFile f = new RandomAccessFile("d://a" + num + ".json", "rw");
                    FileChannel channel = f.getChannel();
                    MappedByteBuffer byteBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, PersistentService.FILE_LENGTH);

                    System.out.println("读取文件d://a"+ num + ".json");
                    if(num != finalFileNum){
                        byte[] bytes = new byte[Math.toIntExact(PersistentService.FILE_LENGTH)];
                        byteBuffer.get(bytes,0, Math.toIntExact(PersistentService.FILE_LENGTH));

                        list.add(num-1,new String(bytes));
                    }else{
                        byte[] bytes = new byte[PersistentService.getInstance().position];
                        byteBuffer.get(bytes,0, PersistentService.getInstance().position);

                        list.add(num-1,new String(bytes));
                    }

                    cyclicBarrier.await();
                }
            });
        }
```

## 4. Semaphore实现请求限流

​ 可以对心跳次数做限制。

```java
private Semaphore semaphore = new Semaphore(10);
try {
            if(semaphore.tryAcquire(1)){
        } finally {
            semaphore.release(1);
        }
```

## 5. 锁优化建议

```java
1.标志位修改等可见性场景优先使用volatile
2.数值递增场景优先使用Atomic原子类
3.读多写少需要加锁的场景优先使用读写锁
4.尽可能减少线程对锁占用的时间
5.尽可能减少线程对数据加锁的粒度
6.尽可能对不同功能分离锁的使用
7.避免在循环中频繁的加锁以及释放锁
8.尽量减少高并发场景中线程对锁的争用
```
