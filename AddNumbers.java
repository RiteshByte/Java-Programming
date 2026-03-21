import java.util.Scanner;

public class AddNumbers {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        int a, b, sum;
        System.out.print("Enter first number: ");
        a = sc.nextInt();

        System.out.print("Enter second number: ");
        b = sc.nextInt();

        sum = a + b;
        System.out.println("Sum = " + sum);
    }
}