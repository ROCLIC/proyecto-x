package app.rpphone.companion;
public final class CallPresentationPolicyTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        boolean[] flags={true,false,true,true,true,true,true,true,true};
        check(test(flags),"Llamada activa bloqueada y autorizada permite solicitud");
        for(int i=0;i<flags.length;i++){
            boolean original=flags[i];flags[i]=!original;
            check(!test(flags),"No abre pantalla si falta requisito "+i);flags[i]=original;
        }
        System.out.println("PASS: pantalla solo bloqueado, llamada activa, sin duplicados, permisos, canal, DND y autorización de segundo plano.");
    }
    static boolean test(boolean[] f){return CallPresentationPolicy.mayOpen(f[0],f[1],f[2],f[3],f[4],f[5],f[6],f[7],f[8]);}
}
