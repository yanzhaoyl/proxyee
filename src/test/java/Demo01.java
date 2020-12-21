/**
 * 类执行顺序
 *
 * 静态代码块--》非静态代码块--》构造方法  [有父类的 先加载 父类的]
 * 静态代码块 只在第一次 new 执行一次，之后不在执行，而非静态代码块在每 new 一次就执行一次
 */
public class Demo01 {

    public static void main(String[] args) {
        new A("111");
        System.out.println("--------");
        new A("222");
        System.out.println("--------");
        new B("333");
    }

}
class A extends B{
    static {
        System.out.println("A 的 静态 代码 块");
    }
    {
        System.out.println("A 的 非静态 代码 块");
    }
    A(){
        System.out.println("A 的 无参构造函数");
    }
    A(String a){
        System.out.println("A 的 有参构造函数 --> " + a);
    }
}
class B{
    static {
        System.out.println("B 的 静态 代码 块");
    }
    {
        System.out.println("B 的 非静态 代码 块");
    }
    B(){
        System.out.println("B 的 无参构造函数");
    }
    B(String b){
        System.out.println("B 的 有参构造函数 --> " + b);
    }
}