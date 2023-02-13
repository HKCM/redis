import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Demo01 {

    static int count = 0;

    public static void request() throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(5);
        count++;
    }

    public static void main(String[] args) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        int threadSize = 100;
        CountDownLatch countDownLatch = new CountDownLatch(threadSize);

        for (int i = 0; i < threadSize; i++) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        for (int j = 0; j < 10; j++) {
                            request();
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    } finally {
                        countDownLatch.countDown();
                    }
                }
            });
            thread.start();
        }
        countDownLatch.await();
        long endTime = System.currentTimeMillis();
        // main,耗时：68,count:994
        System.out.println(Thread.currentThread().getName() + ",耗时：" + (endTime - startTime) + ",count:" + count);
        /**
         Q：分析一下问题出在哪呢？
         A：count++操作实际上是由3步来完成！(jvm执行引擎)
         1. 获取count的值，记做A：A=count
         2. 将A值1，得到B：B=A+1
         3. 将B值赋值给count
         如果有A。B两个线程同时执行count++，
         他们同时执行到上面步骤的第一步，得到的 count。是一样的，3步操作结束后，count只加1，导致count结果不正确！

         java中synchronized关键字和ReentrantLock都可以实现对资源加锁,保证并发正确性,多线程的情况下可以保证被锁住的资源被“串行”访问。
         */
    }
}
