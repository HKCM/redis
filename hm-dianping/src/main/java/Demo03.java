import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Demo03 {

    volatile static int count = 0;

    /**
     * 在request上加锁保证了并发情况下request方法在同一时刻只有一个进程访问，相当于串行执行
     *
     * 如何解决耗时时长问题
     * 在demo1中知道count++实际由三个步骤完成
     * 1. 获取count的值，记做A：A=count
     * 2. 将A值+1，得到B：B=A+1
     * 这里升级第三步的实现
     *      1。获取锁
     *      2。获取count最新值记做LV
     *      3。判断LV是否等于A，如果相等则将B赋值给count，并返回true否则返回false
     *      4。释放锁
     *
     */
    public static void request() throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(5);
        // count++;
        int expectCount;
        while ( !compareAndSwap(expectCount = getCount(), expectCount + 1)) {}
    }

    /**
     *
     * @param expectCount 期望值count
     * @param newCount 需要给count赋得新值
     * @return 成功返回true
     */
    public static synchronized boolean compareAndSwap(int expectCount, int newCount){
        // 判断count当前值是否与期望值一致
        if( getCount() == expectCount){
            count = newCount;
            return true;
        }
        return false;
    }

    public static int getCount() {
        return count;
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
        // main,耗时：6138,count:1000 耗时提高百倍
        System.out.println(Thread.currentThread().getName() + ",耗时：" + (endTime - startTime) + ",count:" + count);
    }
}
