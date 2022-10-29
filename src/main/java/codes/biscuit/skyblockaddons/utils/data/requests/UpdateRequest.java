package codes.biscuit.skyblockaddons.utils.data.requests;

import codes.biscuit.skyblockaddons.SkyblockAddons;
import codes.biscuit.skyblockaddons.core.UpdateInfo;
import codes.biscuit.skyblockaddons.utils.data.JSONResponseHandler;
import codes.biscuit.skyblockaddons.utils.data.RemoteFileRequest;

import java.util.Objects;
import java.util.concurrent.ExecutionException;

public class UpdateRequest extends RemoteFileRequest<UpdateInfo> {
    public UpdateRequest() {
        super("https://raw.githubusercontent.com/FelinaAvis/SkyblockAddons-Unlocked/stable/src/main/resources/update.json",
                new JSONResponseHandler<>(UpdateInfo.class), false, true);
    }

    @Override
    public void load() throws InterruptedException, ExecutionException, RuntimeException {
        SkyblockAddons main = SkyblockAddons.getInstance();
        main.setUpdateInfo(Objects.requireNonNull(getResult(), NO_DATA_RECEIVED_ERROR));
        main.getUpdater().checkForUpdate();
    }
}
