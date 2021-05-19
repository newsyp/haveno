package haveno.core.offer.placeoffer.tasks;

import haveno.core.locale.Res;
import haveno.core.offer.placeoffer.PlaceOfferModel;

import haveno.common.taskrunner.Task;
import haveno.common.taskrunner.TaskRunner;

public class CheckNumberOfUnconfirmedTransactions extends Task<PlaceOfferModel> {
    public CheckNumberOfUnconfirmedTransactions(TaskRunner<PlaceOfferModel> taskHandler, PlaceOfferModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void run() {
        if (model.getWalletService().isUnconfirmedTransactionsLimitHit() || model.getBsqWalletService().isUnconfirmedTransactionsLimitHit())
            failed(Res.get("shared.unconfirmedTransactionsLimitReached"));
        complete();
    }
}
