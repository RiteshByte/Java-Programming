class NumberPrinter implements Runnable 
{
    @Override
    public void run()
    {
        for (int i = 1; i <= 9; i++)
        {
            System.out.println("Number : " + i);
            try
            {
                Thread.sleep(200);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        }
    }
}

class AlphabetPrinter implements Runnable 
{
    @Override
    public void run()
    {
        for (char c = 'A'; c <= 'J'; c++)
        {
            System.out.println("Alphabet : " + c);
            try
            {
                Thread.sleep(250);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        }
    }
}

public class MultithreadingDemo
{
    public static void main(String[] args)
    {
        Thread t1 = new Thread(new NumberPrinter());
        Thread t2 = new Thread(new AlphabetPrinter());

        t1.start();
        t2.start();

        try
        {
            t1.join();
            t2.join();
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }

        System.out.println("Execution Completed!");
    }
}