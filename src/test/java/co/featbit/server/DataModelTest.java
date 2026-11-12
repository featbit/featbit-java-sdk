package co.featbit.server;

import co.featbit.server.exterior.DataStorageTypes;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertSame;

class DataModelTest {

    @Test
    void toStorageTypeKeepsFlagWithLatestTimestampForDuplicateKey() {
        DataModel.FeatureFlag newestFlag = new DataModel.FeatureFlag(
                "new-id", new Date(2000), false, true, "flag", "same-key", "boolean",
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), null, null);
        DataModel.FeatureFlag olderFlag = new DataModel.FeatureFlag(
                "old-id", new Date(1000), false, true, "flag", "same-key", "boolean",
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), null, null);
        newestFlag.afterDeserialization();
        olderFlag.afterDeserialization();
        DataModel.Data data = new DataModel.Data(
                "full", Arrays.asList(newestFlag, olderFlag), Collections.emptyList());

        assertSame(newestFlag, data.toStorageType().get(DataStorageTypes.FEATURES).get("same-key"));
    }
}
