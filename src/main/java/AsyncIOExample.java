import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;  
import org.apache.flink.api.common.serialization.SimpleStringSchema;  
import org.apache.flink.streaming.api.datastream.AsyncDataStream;  
import org.apache.flink.streaming.api.datastream.DataStream;  
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;  
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;  
import org.apache.flink.streaming.api.functions.async.AsyncFunction;  
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer;  
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisProducer;  
import org.apache.flink.streaming.connectors.kinesis.config.ConsumerConfigConstants;  
import org.slf4j.Logger;  
import org.slf4j.LoggerFactory;  
  
import java.util.Properties;  
import java.util.concurrent.TimeUnit;  
  
public class AsyncIOExample {  
  
    private static DataStream<String> createSourceFromStaticConfig(StreamExecutionEnvironment env, String region, String inputStreamName) {  
  
        Properties inputProperties = new Properties();  
        inputProperties.setProperty(ConsumerConfigConstants.AWS_REGION, region);  
        inputProperties.setProperty(ConsumerConfigConstants.STREAM_INITIAL_POSITION, "LATEST");  
        inputProperties.setProperty(ConsumerConfigConstants.SHARD_USE_ADAPTIVE_READS, "true");  
        return env.addSource(new FlinkKinesisConsumer<>(  
                inputStreamName,  
                new SimpleStringSchema(),  
                inputProperties))  
                .name("flink_kinesis_consumer_01").setParallelism(1);  
    }  
  
    private static FlinkKinesisProducer<String> createSinkFromStaticConfig(String region, String outputStreamName) {  
        Properties outputProperties = new Properties();  
        outputProperties.setProperty(ConsumerConfigConstants.AWS_REGION, region);  
        outputProperties.setProperty("AggregationEnabled", "false");  
        FlinkKinesisProducer<String> sink = new FlinkKinesisProducer<>(new SimpleStringSchema(), outputProperties);  
        sink.setDefaultStream(outputStreamName);  
        sink.setDefaultPartition("0");  
        return sink;  
    }  
  
    private static FlinkKinesisProducer<String> createKinesisSinkFromStaticConfig(String region, String kinesisOutputStreamName) {  
        Properties outputProperties = new Properties();  
        outputProperties.setProperty(ConsumerConfigConstants.AWS_REGION, region);  
        outputProperties.setProperty("AggregationEnabled", "false");  
        FlinkKinesisProducer<String> sink = new FlinkKinesisProducer<>(new SimpleStringSchema(), outputProperties);  
        sink.setDefaultStream(kinesisOutputStreamName);  
        sink.setDefaultPartition("0");  
        return sink;  
    }  
  
    public static void main(String[] args) throws Exception {  
  
        final Logger LOG = LoggerFactory.getLogger(AsyncIOExample.class);  
        final String WAIT_MODE;  
        final String REGION;  
        final String INPUT_STREAM_NAME;  
        final String OUTPUT_STREAM_NAME;  
        final String KINESIS_OUTPUT_STREAM_NAME;  
        int ASYNC_OPERATOR_PARALLELISM;  
        final long ASYNC_OPERATOR_TIMEOUT;  
        final int ASYNC_OPERATOR_CAPACITY;  
        final String POST_REQUEST_URL;  
  
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();  
        Properties applicationProperties;  
  
        
        applicationProperties = KinesisAnalyticsRuntime.getApplicationProperties().get("FlinkApplicationProperties");  
  
  
        try {  
            WAIT_MODE = applicationProperties.getProperty("wait_mode", "unordered");  
            REGION = applicationProperties.getProperty("region", "eu-central-1");  
            INPUT_STREAM_NAME = applicationProperties.getProperty("input_stream_name");  
            KINESIS_OUTPUT_STREAM_NAME = applicationProperties.getProperty("kinesis_output_stream_name");  
            ASYNC_OPERATOR_PARALLELISM = Integer.parseInt(applicationProperties.getProperty("waitOperatorParallelism", String.valueOf(env.getParallelism())));  
            ASYNC_OPERATOR_TIMEOUT = Long.parseLong(applicationProperties.getProperty("timeout", "30000"));  
            ASYNC_OPERATOR_CAPACITY = Integer.parseInt(applicationProperties.getProperty("capacity", "1"));  
            POST_REQUEST_URL = applicationProperties.getProperty("post_request_url");  
        } catch (Exception e) {  
            throw e;  
        }  
  
        DataStream<String> inputStream = createSourceFromStaticConfig(env, REGION, INPUT_STREAM_NAME);  
  
        inputStream.map(new NoOpMapperFunction("async-capacity", ASYNC_OPERATOR_CAPACITY)).setParallelism(1);  
  
        AsyncFunction<String, String> customAsyncFunction = new SampleAsyncFunction(POST_REQUEST_URL);  
  
        DataStream<String> result;  
        if (WAIT_MODE.equals("ordered")) {  
            result = AsyncDataStream.orderedWait(  
                    inputStream,  
                    customAsyncFunction,  
                    ASYNC_OPERATOR_TIMEOUT,  
                    TimeUnit.MILLISECONDS,  
                    ASYNC_OPERATOR_CAPACITY).setParallelism(ASYNC_OPERATOR_PARALLELISM).disableChaining();  
        } else {  
            result = AsyncDataStream.unorderedWait(  
                    inputStream,  
                    customAsyncFunction,  
                    ASYNC_OPERATOR_TIMEOUT,  
                    TimeUnit.MILLISECONDS,  
                    ASYNC_OPERATOR_CAPACITY).setParallelism(ASYNC_OPERATOR_PARALLELISM).disableChaining();  
        }  
  
        inputStream.map(new SampleSyncFunction(POST_REQUEST_URL)).setParallelism(ASYNC_OPERATOR_PARALLELISM).disableChaining();  

  
        // Add the new Kinesis sink to the result DataStream  
        result.addSink(createKinesisSinkFromStaticConfig(REGION, KINESIS_OUTPUT_STREAM_NAME)).name("flink_kinesis_producer_kinesis_output");  
  
        // execute the program  
        env.execute("Async IO Example with Kinesis Output");  
    }  
}  

