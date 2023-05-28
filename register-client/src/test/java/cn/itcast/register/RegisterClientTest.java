package cn.itcast.register;

import cn.itcast.register.client.RegisterClient;

import java.util.concurrent.TimeUnit;

public class RegisterClientTest {

    public static void main(String[] args) throws InterruptedException {
        RegisterClient client = new RegisterClient();
        client.start();

        TimeUnit.SECONDS.sleep(10);

        client.shutdown();
    }
}
