package gov.nysenate.openleg.client.view.bill;

import gov.nysenate.openleg.client.view.base.ViewObject;
import gov.nysenate.openleg.model.bill.BillUpdateDigest;
import org.apache.commons.lang3.text.WordUtils;

import java.time.LocalDateTime;
import java.util.Map;

public class BillUpdateDigestView implements ViewObject
{
    private String action;
    private String scope;
    private Map<String, String> updates;
    private LocalDateTime updatedOn;
    private String sourceId;
    private LocalDateTime sourceDateTime;

    public BillUpdateDigestView(BillUpdateDigest updateDigest) {
        if (updateDigest != null) {
            this.action = updateDigest.getAction();
            this.scope = WordUtils.capitalizeFully(updateDigest.getTable().replaceAll("_", " "));
            this.updates = updateDigest.getUpdates();
            this.updatedOn = updateDigest.getUpdatedDateTime();
            this.sourceId = updateDigest.getSourceDataId();
            this.sourceDateTime = updateDigest.getSourceDataDateTime();
        }
    }

    @Override
    public String getViewType() {
        return "bill-update-digest";
    }

    public String getAction() {
        return action;
    }

    public String getScope() {
        return scope;
    }

    public Map<String, String> getUpdates() {
        return updates;
    }

    public LocalDateTime getUpdatedOn() {
        return updatedOn;
    }

    public String getSourceId() {
        return sourceId;
    }

    public LocalDateTime getSourceDateTime() {
        return sourceDateTime;
    }
}
