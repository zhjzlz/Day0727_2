import atguigu.entity.LoginEvent;
import atguigu.entity.LoginWarning;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.IterativeCondition;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class FlinkLoginFail {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 这里mock了事件流，这个事件流一般从Kafka过来
        DataStream<LoginEvent> loginEventStream = env.fromCollection(Arrays.asList(
                new LoginEvent("1","192.168.0.1","fail"),
                new LoginEvent("1","192.168.0.2","fail"),
                new LoginEvent("1","192.168.0.3","fail"),
                new LoginEvent("2","192.168.10,10","success")
        ));

        Pattern<LoginEvent, LoginEvent> loginFailPattern = Pattern.<LoginEvent>
                // 开始的名字随便起，这里取了"begin"，也可以是"xxxxx"
                begin("begin")
                // 模式开始事件的匹配条件为事件类型为fail, 为迭代条件
                .where(new IterativeCondition<LoginEvent>() {
                    @Override
                    public boolean filter(LoginEvent loginEvent, Context context) throws Exception {
                        return loginEvent.getType().equals("fail");
                    }
                })
                // 紧邻的事件为next, 起名"next", next的含义是紧随其后的意思
                .next("next")
                // 事件的匹配条件为事件类型为fail
                .where(new IterativeCondition<LoginEvent>() {
                    @Override
                    public boolean filter(LoginEvent loginEvent, Context context) throws Exception {
                        return loginEvent.getType().equals("fail");
                    }
                })
                // 要求紧邻的两个事件发生的时间间隔不能超过3秒钟
                .within(Time.seconds(3));

        // 以userid分组，形成keyedStream，然后进行模式匹配
        PatternStream<LoginEvent> patternStream = CEP.pattern(
                loginEventStream.keyBy(LoginEvent::getUserId),
                loginFailPattern);

        DataStream<LoginWarning> loginFailDataStream = patternStream.select((Map<String, List<LoginEvent>> pattern) -> {
            List<LoginEvent> first = pattern.get("begin");
            List<LoginEvent> second = pattern.get("next");

            return new LoginWarning(second.get(0).getUserId(),second.get(0).getIp(), second.get(0).getType());
        });

        loginFailDataStream.print();

        env.execute();
    }

}