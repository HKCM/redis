import java.util.concurrent.atomic.AtomicInteger;
/**
 * ABA问题演示
 *
 * 解决方案：AtomicStampReference
 * */
public class ABADemo01 {
    public static AtomicInteger a = new AtomicInteger(1);

    public static void main(String[] args) {
        Thread main = new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("Thread:" + Thread.currentThread().getName() + ", 初始值：" + a.get());
                try {
                    int expectNum = a.get();
                    int newNum = expectNum + 1;
                    Thread.sleep(1000);

                    boolean isCASSuccess = a.compareAndSet(expectNum,newNum);
                    System.out.println("Thread:" + Thread.currentThread().getName() + ", CAS操作：" + isCASSuccess);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        },"主线程");

        Thread other = new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    Thread.sleep(20); // 确保main thread优先执行
                    a.incrementAndGet(); // a + 1
                    System.out.println("Thread:" + Thread.currentThread().getName() + ", [increment] ：" + a.get());
                    a.decrementAndGet(); // a - 1
                    System.out.println("Thread:" + Thread.currentThread().getName() + ", [decrement] ：" + a.get());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        },"干扰线程");

        main.start();
        other.start();
    }
}
