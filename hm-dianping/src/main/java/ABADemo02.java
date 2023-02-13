import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicStampedReference;

/**
 * ABA问题演示
 *
 * 解决方案：AtomicStampReference
 * */
public class ABADemo02 {
    public static AtomicStampedReference<Integer> a = new AtomicStampedReference(Integer.valueOf(1),1);

    public static void main(String[] args) {
        Thread main = new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("Thread:" + Thread.currentThread().getName() + ", 初始值：" + a.getReference());
                try {
                    Integer expectNum = a.getReference();
                    Integer newNum = expectNum + 1;
                    int expectStamp = a.getStamp();
                    int newStamp = expectStamp + 1;
                    Thread.sleep(1000);

                    boolean isCASSuccess = a.compareAndSet(expectNum,newNum,expectStamp,newStamp + 1);
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
                    a.compareAndSet(a.getReference(), a.getReference() + 1, a.getStamp(),a.getStamp() + 1 ); // a + 1
                    System.out.println("Thread:" + Thread.currentThread().getName() + ", [increment] ：" + a.getReference());
                    a.compareAndSet(a.getReference(), a.getReference() - 1, a.getStamp(),a.getStamp() + 1 ); // a - 1
                    System.out.println("Thread:" + Thread.currentThread().getName() + ", [decrement] ：" + a.getReference());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        },"干扰线程");

        main.start();
        other.start();
    }
}
