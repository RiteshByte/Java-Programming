import java.util.Scanner;

public class Largest {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        int a, b;
        System.out.print("Enter two numbers: ");
        a = sc.nextInt();
        b = sc.nextInt();

        if (a > b)
            System.out.println("Largest: " + a);
        else
            System.out.println("Largest: " + b);
    }
}
