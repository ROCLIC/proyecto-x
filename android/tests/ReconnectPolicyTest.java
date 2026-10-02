package app.rpphone.companion;
public final class ReconnectPolicyTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        ReconnectPolicy p=new ReconnectPolicy();
        check(!p.allowed(0,false,false,false),"No recarga sin internet");
        check(!p.allowed(0,true,true,false),"No interrumpe llamadas/audio activo");
        check(!p.allowed(0,true,false,true),"No interrumpe autenticación Steam");
        p.login=true;check(!p.allowed(0,true,false,false),"No repite sesión vencida");p.ready();
        check(p.allowed(0,true,false,false),"Permite recuperar conexión");
        long[] expected={5000,10000,20000,40000,60000,60000};
        for(long delay:expected)check(p.delay()==delay,"Reintentos espaciados y acotados");
        p.next=120000;check(!p.allowed(119999,true,false,false),"Respeta espera");
        check(p.allowed(120000,true,false,false),"Reintenta al cumplir espera");
        p.ready();check(p.delay()==5000 && p.next==0 && !p.login,"Recuperación reinicia política");
        System.out.println("PASS: reintentos, ausencia de red, llamada activa, autenticación y sesión vencida.");
    }
}
