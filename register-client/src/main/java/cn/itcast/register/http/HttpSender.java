package cn.itcast.register.http;

import cn.itcast.register.pojo.HeartBeatRequest;
import cn.itcast.register.pojo.HeartBeatResponse;
import cn.itcast.register.pojo.RegisterRequest;
import cn.itcast.register.pojo.RegisterResponse;
import com.alibaba.fastjson.JSON;
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
 * 封装http请求
 *  1. 发送注册请求
 *  2. 发送心跳请求
 */
public class HttpSender {

    private final String  REGISTER_URL = "http://localhost:8080/server/register";
    private final String  HEARTBEAT_URL = "http://localhost:8080/server/heartBeat";

    /**
     * 注册
     */
    public RegisterResponse register(RegisterRequest request) throws IOException {
        //1. 创建httpclient的对象
        CloseableHttpClient client = HttpClients.createDefault();

        //2. 创建post请求
        HttpPost post = new HttpPost(REGISTER_URL);

        //3. 设置请求的参数和编码
        StringEntity entity = new StringEntity(JSON.toJSONString(request), ContentType.APPLICATION_JSON);
        entity.setContentEncoding("UTF-8");
        post.setEntity(entity);

        //4. 发送post请求
        CloseableHttpResponse response = client.execute(post);

        //5. 解析返回的结果
        HttpEntity httpEntity = response.getEntity();
        String json = EntityUtils.toString(httpEntity);
        RegisterResponse resp = JSON.parseObject(json, RegisterResponse.class);

        System.out.println(request.getServiceName() + " 进行了服务的注册");

        return resp;
    }

    /**
     * 心跳请求
     * @param request
     * @return
     */
    public HeartBeatResponse heartBeat(HeartBeatRequest request) throws IOException {
        System.out.println(request.getServiceInstanceId() + " 心跳请求发送成功");

        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost post = new HttpPost(HEARTBEAT_URL);

        StringEntity entity = new StringEntity(JSON.toJSONString(request), ContentType.APPLICATION_JSON);
        entity.setContentEncoding("UTF-8");
        post.setEntity(entity);

        CloseableHttpResponse response = client.execute(post);

        HttpEntity httpEntity = response.getEntity();

        String json = EntityUtils.toString(httpEntity);

        HeartBeatResponse resp = JSON.parseObject(json, HeartBeatResponse.class);

        return resp;
    }
}
