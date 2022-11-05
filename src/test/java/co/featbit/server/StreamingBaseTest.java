package co.featbit.server;

import co.featbit.commons.json.JsonHelper;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;

abstract class StreamingBaseTest {

    protected DataModel.Data loadData() throws Exception {
        DataModel.All all = JsonHelper.deserialize(Resources.toString(Resources.getResource("fbclient_test_data.json"), Charsets.UTF_8),
                DataModel.All.class);
        return (all.isProcessData()) ? all.data() : null;
    }
}
