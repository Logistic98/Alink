package com.alibaba.alink.operator.common.recommendation;

import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.ml.api.misc.param.ParamInfo;
import org.apache.flink.ml.api.misc.param.Params;
import org.apache.flink.table.api.TableSchema;
import org.apache.flink.types.Row;
import org.apache.flink.util.Preconditions;

import com.alibaba.alink.common.MTable;
import com.alibaba.alink.common.linalg.Vector;
import com.alibaba.alink.common.linalg.VectorUtil;
import com.alibaba.alink.common.utils.JsonConverter;
import com.alibaba.alink.operator.common.utils.PackBatchOperatorUtil;
import com.alibaba.alink.params.recommendation.BaseItemsPerUserRecommParams;
import com.alibaba.alink.params.recommendation.BaseUsersPerItemRecommParams;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VecDotRecommKernel extends RecommKernel {

	private static final long serialVersionUID = 2716744280007281817L;
	protected transient Map <Object, Vector> userFactors;
	protected transient Map <Object, Vector> itemFactors;
	protected transient Map <Object, Set <Object>> historyUserItems;
	protected transient Map <Object, Set <Object>> historyItemUsers;

	private final Integer topK;
	private boolean excludeKnown = false;

	public VecDotRecommKernel(TableSchema modelSchema, TableSchema dataSchema, Params params, RecommType recommType) {
		super(modelSchema, dataSchema, params, recommType);
		this.topK = getParamDefaultAsNull(params, BaseItemsPerUserRecommParams.K);

		if (recommType == RecommType.ITEMS_PER_USER) {
			Preconditions.checkArgument(topK != null, "Missing param topK");
			excludeKnown = params.get(BaseItemsPerUserRecommParams.EXCLUDE_KNOWN);
		} else if (recommType == RecommType.USERS_PER_ITEM) {
			Preconditions.checkArgument(topK != null, "Missing param topK");
			excludeKnown = params.get(BaseUsersPerItemRecommParams.EXCLUDE_KNOWN);
		} else if (recommType == RecommType.SIMILAR_USERS) {
			Preconditions.checkArgument(topK != null, "Missing param topK");
		} else if (recommType == RecommType.SIMILAR_ITEMS) {
			Preconditions.checkArgument(topK != null, "Missing param topK");
		}
	}

	private static <T> T getParamDefaultAsNull(Params params, ParamInfo <T> paramInfo) {
		if (params.contains(paramInfo)) {
			return params.get(paramInfo);
		} else {
			if (paramInfo.hasDefaultValue()) {
				return paramInfo.getDefaultValue();
			} else {
				return null;
			}
		}
	}

	@Override
	public void loadModel(List <Row> modelRows) {
		for (Row row : modelRows) {
			if (((Number) row.getField(0)).intValue() == -1) {
				Tuple2 <List <List <String>>, List <List <Integer>>> metaData =
					JsonConverter.fromJson((String) row.getField(1), Tuple2.class);
				userColName = metaData.f0.get(2).get(0);
				itemColName = metaData.f0.get(2).get(1);
				int userIdx = metaData.f1.get(2).get(0);
				int itemIdx = metaData.f1.get(2).get(1);
				if (recommType == RecommType.ITEMS_PER_USER) {
					recommObjType = getModelSchema().getFieldTypes()[itemIdx];
				} else if (recommType == RecommType.USERS_PER_ITEM) {
					recommObjType = getModelSchema().getFieldTypes()[userIdx];
				} else if (recommType == RecommType.SIMILAR_USERS) {
					recommObjType = getModelSchema().getFieldTypes()[userIdx];
				} else if (recommType == RecommType.SIMILAR_ITEMS) {
					recommObjType = getModelSchema().getFieldTypes()[itemIdx];
				}
			}
		}

		List <Row> userFactorsRows = PackBatchOperatorUtil.unpackRows(modelRows, 0);
		List <Row> itemFactorsRows = PackBatchOperatorUtil.unpackRows(modelRows, 1);

		userFactors = new HashMap <>();
		itemFactors = new HashMap <>();

		userFactorsRows.forEach(row -> {
			userFactors.put(row.getField(0), VectorUtil.getVector(row.getField(1)));
		});

		itemFactorsRows.forEach(row -> {
			itemFactors.put(row.getField(0), VectorUtil.getVector(row.getField(1)));
		});

		if (excludeKnown) {
			List <Row> history = PackBatchOperatorUtil.unpackRows(modelRows, 2);
			historyItemUsers = new HashMap <>();
			historyUserItems = new HashMap <>();
			for (Row row : history) {
				Object user = row.getField(0);
				Object item = row.getField(1);
				if (historyUserItems.containsKey(user)) {
					historyUserItems.get(user).add(item);
				} else {
					HashSet <Object> items = new HashSet <>();
					items.add(item);
					historyUserItems.put(user, items);
				}
				if (historyItemUsers.containsKey(item)) {
					historyItemUsers.get(item).add(user);
				} else {
					HashSet <Object> users = new HashSet <>();
					users.add(user);
					historyItemUsers.put(item, users);
				}
			}
		}
	}

	@Override
	Double rate(Object[] ids) throws Exception {
		return predictRating(ids);
	}

	@Override
	public MTable recommendItemsPerUser(Object userId) throws Exception {
		Vector userFea = userFactors.get(userId);
		Set <Object> excludes = null;
		if (excludeKnown) {
			excludes = historyUserItems.get(userId);
		}
		return recommend(itemColName, userFea, excludes, itemFactors, KObjectUtil.RATING_NAME);
	}

	@Override
	public MTable recommendUsersPerItem(Object itemId) throws Exception {
		Vector itemFea = itemFactors.get(itemId);
		Set <Object> excludes = null;
		if (excludeKnown) {
			excludes = historyItemUsers.get(itemId);
		}
		return recommend(userColName, itemFea, excludes, userFactors, KObjectUtil.RATING_NAME);
	}

	@Override
	public MTable recommendSimilarItems(Object itemId) throws Exception {
		Vector itemFea = itemFactors.get(itemId);
		Set <Object> excludes = new HashSet <>();
		excludes.add(itemId);
		return recommend(itemColName, itemFea, excludes, itemFactors, KObjectUtil.SCORE_NAME);
	}

	@Override
	public MTable recommendSimilarUsers(Object userId) throws Exception {
		Vector userFea = userFactors.get(userId);
		Set <Object> excludes = new HashSet <>();
		excludes.add(userId);
		return recommend(userColName, userFea, excludes, userFactors, KObjectUtil.SCORE_NAME);
	}

	private Double predictRating(Object[] ids) throws Exception {
		Object userId = ids[0];
		Object itemId = ids[1];
		Vector userFea = userFactors.get(userId);
		Vector itemFea = itemFactors.get(itemId);
		if (userFea != null && itemFea != null) {
			return userFea.dot(itemFea);
		} else {
			return null; // unknown user or item
		}
	}

	private MTable recommend(String objectName, Vector userFea, Set <Object> excludes,
							 Map <Object, Vector> itemFeatures, String resultName) {
		RecommUtils.RecommPriorityQueue priorQueue = new RecommUtils.RecommPriorityQueue(topK);

		if (userFea != null) {
			itemFeatures.forEach((itemId, itemFea) -> {
				if (excludes == null || !excludes.contains(itemId)) {
					priorQueue.addOrReplace(itemId, userFea.dot(itemFea));
				}
			});
		}
		List <Row> rows = priorQueue.getOrderedRows();
		return new MTable(rows, objectName + " " + recommObjType.toString() + "," + resultName+ " DOUBLE");
	}

	@Override
	public RecommKernel createNew() {
		return new VecDotRecommKernel(getModelSchema(), getDataSchema(), params.clone(), recommType);
	}
}
