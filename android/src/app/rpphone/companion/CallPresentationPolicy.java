package app.rpphone.companion;
final class CallPresentationPolicy {
    static boolean mayOpen(boolean active,boolean shown,boolean locked,boolean enabled,boolean notifications,boolean fullScreen,boolean highChannel,boolean dndAllows,boolean backgroundAllowed){
        return active && !shown && locked && enabled && notifications && fullScreen && highChannel && dndAllows && backgroundAllowed;
    }
}
