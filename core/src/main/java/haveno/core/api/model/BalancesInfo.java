package haveno.core.api.model;

import haveno.common.Payload;

import lombok.Getter;

@Getter
public class BalancesInfo implements Payload {

    // Getter names are shortened for readability's sake, i.e.,
    // balancesInfo.getBtc().getAvailableBalance() is cleaner than
    // balancesInfo.getBtcBalanceInfo().getAvailableBalance().
    private final BsqBalanceInfo bsq;
    private final BtcBalanceInfo btc;
    private final XmrBalanceInfo xmr;

    public BalancesInfo(BsqBalanceInfo bsq, BtcBalanceInfo btc, XmrBalanceInfo xmr) {
        this.bsq = bsq;
        this.btc = btc;
        this.xmr = xmr;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public haveno.proto.grpc.BalancesInfo toProtoMessage() {
        return haveno.proto.grpc.BalancesInfo.newBuilder()
                .setBsq(bsq.toProtoMessage())
                .setBtc(btc.toProtoMessage())
                .setXmr(xmr.toProtoMessage())
                .build();
    }

    public static BalancesInfo fromProto(bisq.proto.grpc.BalancesInfo proto) {
        return new BalancesInfo(BsqBalanceInfo.fromProto(proto.getBsq()),
                BtcBalanceInfo.fromProto(proto.getBtc()),
                XmrBalanceInfo.fromProto(proto.getXmr()));
    }

    @Override
    public String toString() {
        return "BalancesInfo{" + "\n" +
                "  " + bsq.toString() + "\n" +
                ", " + btc.toString() + "\n" +
                ", " + xmr.toString() + "\n" +
                '}';
    }
}
