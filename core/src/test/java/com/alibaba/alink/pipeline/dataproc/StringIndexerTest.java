package com.alibaba.alink.pipeline.dataproc;

import org.apache.flink.types.Row;

import com.alibaba.alink.operator.batch.BatchOperator;
import com.alibaba.alink.operator.batch.source.MemSourceBatchOp;
import com.alibaba.alink.operator.common.dataproc.SortUtils.RowComparator;
import com.alibaba.alink.operator.stream.StreamOperator;
import com.alibaba.alink.operator.stream.sink.CollectSinkStreamOp;
import com.alibaba.alink.operator.stream.source.MemSourceStreamOp;
import com.alibaba.alink.testutil.AlinkTestBase;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test cases for {@link StringIndexer}.
 */

public class StringIndexerTest extends AlinkTestBase {
	private static Row[] rows = new Row[] {
		Row.of("football"),
		Row.of("football"),
		Row.of("football"),
		Row.of("basketball"),
		Row.of("basketball"),
		Row.of("tennis"),
	};

	private static void checkResult(List <Row> prediction, String[] actualOrderedTokens) {
		Map <String, Long> actual = new HashMap <>();
		for (int i = 0; i < actualOrderedTokens.length; i++) {
			actual.put(actualOrderedTokens[i], (long) i);
		}

		prediction.forEach(row -> {
			String token = (String) row.getField(0);
			Long id = (Long) row.getField(1);
			Assert.assertEquals(id, actual.get(token));
		});
	}

	@Test
	public void testRandom() throws Exception {
		BatchOperator data = new MemSourceBatchOp(Arrays.asList(rows), new String[] {"f0"});

		StringIndexer stringIndexer = new StringIndexer()
			.setSelectedCol("f0")
			.setOutputCol("f0_indexed")
			.setStringOrderType("random");

		Assert.assertEquals(stringIndexer.fit(data).getModelData().collect().size(), 3);
	}

	@Test
	public void testFrequencyAsc() throws Exception {
		BatchOperator data = new MemSourceBatchOp(Arrays.asList(rows), new String[] {"f0"});

		StringIndexer stringIndexer = new StringIndexer()
			.setSelectedCol("f0")
			.setOutputCol("f0_indexed")
			.setStringOrderType("frequency_asc");

		List <Row> prediction = stringIndexer.fit(data).transform(data).collect();
		checkResult(prediction, new String[] {"tennis", "basketball", "football"});

		StreamOperator streamData = new MemSourceStreamOp(Arrays.asList(rows), new String[] {"f0"});
		CollectSinkStreamOp collectSinkStreamOp = new CollectSinkStreamOp()
			.linkFrom(stringIndexer.fit(data).transform(streamData));
		StreamOperator.execute();
		List <Row> result = collectSinkStreamOp.getAndRemoveValues();
		checkResult(result, new String[] {"tennis", "basketball", "football"});
	}

	@Test
	public void testAlphabetDesc() throws Exception {
		BatchOperator data = new MemSourceBatchOp(Arrays.asList(rows), new String[] {"f0"});

		StringIndexer stringIndexer = new StringIndexer()
			.setSelectedCol("f0")
			.setOutputCol("f0_indexed")
			.setStringOrderType("alphabet_desc");

		List <Row> prediction = stringIndexer.fit(data).transform(data).collect();
		checkResult(prediction, new String[] {"tennis", "football", "basketball"});

		StreamOperator streamData = new MemSourceStreamOp(Arrays.asList(rows), new String[] {"f0"});
		CollectSinkStreamOp collectSinkStreamOp = new CollectSinkStreamOp()
			.linkFrom(stringIndexer.fit(data).transform(streamData));
		StreamOperator.execute();
		List <Row> result = collectSinkStreamOp.getAndRemoveValues();
		checkResult(result, new String[] {"tennis", "football", "basketball"});
	}
}