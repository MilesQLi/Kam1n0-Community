/*******************************************************************************
 * Copyright 2017 McGill University All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package ca.mcgill.sis.dmas.kam1n0.problem.clone.detector.kam.index;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.Row;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import static com.datastax.driver.core.querybuilder.QueryBuilder.*;
import static com.datastax.spark.connector.japi.CassandraJavaUtil.javaFunctions;
import ca.mcgill.sis.dmas.env.StringResources;
import ca.mcgill.sis.dmas.io.LineSequenceWriter;
import ca.mcgill.sis.dmas.io.Lines;
import ca.mcgill.sis.dmas.kam1n0.utils.datastore.CassandraInstance;
import ca.mcgill.sis.dmas.kam1n0.utils.executor.SparkInstance;

public class LshAdaptiveBucketIndexCassandra extends LshAdaptiveBucketIndexAbstract {

	private static Logger logger = LoggerFactory.getLogger(LshAdaptiveDupIndexCasandra.class);

	private CassandraInstance cassandraInstance;
	private String databaseName = StringResources.STR_EMPTY;

	public LshAdaptiveBucketIndexCassandra(SparkInstance sparkInstance, CassandraInstance cassandraInstance,
			int initialDepth, int maxDepth, int maxSize, Function<Integer, Integer> nextDepth, String databaseName) {
		super(sparkInstance, initialDepth, maxDepth, maxSize, nextDepth);
		this.cassandraInstance = cassandraInstance;
		this.databaseName = databaseName;
	}

	@Override
	public void init() {
		this.createSchema();
	}

	// classes:
	public static final String _ADAPTIVE_BUCK = "ADAPTIVE_BUCK".toLowerCase();

	// properties:
	public static final String _APP_ID = "rid0";
	public static final String _ADAPTIVE_BUCK_PKEY = "pkey";
	public static final String _ADAPTIVE_BUCK_CKEY = "ckey";
	public static final String _ADAPTIVE_BUCK_DEPTH = "depth";
	public static final String _ADAPTIVE_BUCK_HIDS = "hids";

	private void createSchema() {
		if (!cassandraInstance.checkColumnFamilies(this.sparkInstance.getConf(), this.databaseName, _ADAPTIVE_BUCK)) {
			logger.info("Creating table {}.{}", databaseName, _ADAPTIVE_BUCK);
			this.cassandraInstance.doWithSession(this.sparkInstance.getConf(), session -> {
				session.execute("CREATE KEYSPACE if not exists " + databaseName + " WITH "
						+ "replication = {'class':'SimpleStrategy', 'replication_factor':1} "
						+ " AND durable_writes = true;");
				session.execute("create table if not exists " + databaseName + "." + _ADAPTIVE_BUCK + " (" //
						+ _APP_ID + " bigint,"//
						+ _ADAPTIVE_BUCK_PKEY + " varchar," //
						+ _ADAPTIVE_BUCK_CKEY + " varchar," //
						+ _ADAPTIVE_BUCK_DEPTH + " int," //
						+ _ADAPTIVE_BUCK_HIDS + " set<bigint>," //
						+ "PRIMARY KEY ((" + _APP_ID + "), " //
						+ _ADAPTIVE_BUCK_PKEY + ", " //
						+ _ADAPTIVE_BUCK_CKEY + ")" //
						+ ");");
			});
		} else {
			logger.info("Found table {}.{}", databaseName, _ADAPTIVE_BUCK);
		}
	}

	@Override
	public void close() {
	}

	@Override
	public HashSet<Long> getHids(long rid, String primaryKey, String secondaryKey) {
		return this.cassandraInstance.doWithSessionWithReturn(this.sparkInstance.getConf(), session -> {
			Row row = session.execute((QueryBuilder.select(_ADAPTIVE_BUCK_HIDS)//
					.from(databaseName, _ADAPTIVE_BUCK))//
							.where(eq(_APP_ID, rid))//
							.and(eq(_ADAPTIVE_BUCK_PKEY, primaryKey))//
							.and(eq(_ADAPTIVE_BUCK_CKEY, secondaryKey)))
					.one();
			// not existed
			if (row == null)
				return null;
			// existed
			if (row.isNull(0))
				return new HashSet<Long>();
			Set<Long> set = row.getSet(0, Long.class);
			if (set instanceof HashSet<?>)
				return (HashSet<Long>) set;
			return new HashSet<Long>(set);
		});
	}

	@Override
	public boolean clearHid(long rid, String primaryKey, String secondaryKey) {
		this.cassandraInstance.doWithSession(this.sparkInstance.getConf(), session -> {
			session.executeAsync(QueryBuilder.update(databaseName, _ADAPTIVE_BUCK)//
					.with(set(_ADAPTIVE_BUCK_HIDS, null))//
					.and(set(_ADAPTIVE_BUCK_DEPTH, 1))//
					.where(eq(_APP_ID, rid))//
					.and(eq(_ADAPTIVE_BUCK_PKEY, primaryKey))//
					.and(eq(_ADAPTIVE_BUCK_CKEY, secondaryKey)));
		});
		return true;
	}

	@Override
	public boolean putHid(long rid, String primaryKey, String secondaryKey, int newDepth, Long hid) {
		this.cassandraInstance.doWithSession(this.sparkInstance.getConf(), session -> {
			session.executeAsync(QueryBuilder.update(databaseName, _ADAPTIVE_BUCK)//
					.with(add(_ADAPTIVE_BUCK_HIDS, hid))//
					.and(set(_ADAPTIVE_BUCK_DEPTH, newDepth))//
					.where(eq(_APP_ID, rid))//
					.and(eq(_ADAPTIVE_BUCK_PKEY, primaryKey))//
					.and(eq(_ADAPTIVE_BUCK_CKEY, secondaryKey)));
		});
		return true;
	}

	@Override
	public boolean putHid(long rid, String primaryKey, String secondaryKey, HashSet<Long> hids) {
		this.cassandraInstance.doWithSession(this.sparkInstance.getConf(), session -> {
			session.executeAsync(QueryBuilder.update(databaseName, _ADAPTIVE_BUCK)//
					.with(QueryBuilder.addAll(_ADAPTIVE_BUCK_HIDS, hids))//
					.where(eq(_APP_ID, rid))//
					.and(eq(_ADAPTIVE_BUCK_PKEY, primaryKey))//
					.and(eq(_ADAPTIVE_BUCK_CKEY, secondaryKey)));
		});
		return true;
	}

	@Override
	public AdaptiveBucket nextOnTheLeft(long rid, AdaptiveBucket target) {
		return this.cassandraInstance.doWithSessionWithReturn(this.sparkInstance.getConf(), session -> {
			Row row = session.execute((QueryBuilder.select()//
					.from(databaseName, _ADAPTIVE_BUCK))//
							.where(eq(_APP_ID, rid))//
							.and(eq(_ADAPTIVE_BUCK_PKEY, target.pkey))//
							.and(lt(_ADAPTIVE_BUCK_CKEY, target.cKey)))
					.one();
			if (row == null || row.isNull(0))
				return null;
			Set<Long> hids = row.getSet(4, Long.class);
			HashSet<Long> hids_h = (hids instanceof HashSet<?>) ? (HashSet<Long>) hids : new HashSet<>(hids);

			AdaptiveBucket buk = new AdaptiveBucket(row.getString(1), row.getString(2), row.getInt(3), hids_h);

			return buk;
		});
	}

	@Override
	public AdaptiveBucket nextOnTheRight(long rid, AdaptiveBucket target) {
		return this.cassandraInstance.doWithSessionWithReturn(this.sparkInstance.getConf(), session -> {
			Row row = session.execute((QueryBuilder.select()//
					.from(databaseName, _ADAPTIVE_BUCK))//
							.where(eq(_APP_ID, rid))//
							.and(eq(_ADAPTIVE_BUCK_PKEY, target.pkey))//
							.and(gt(_ADAPTIVE_BUCK_CKEY, target.cKey)))
					.one();
			if (row == null || row.isNull(1))
				return null;
			Set<Long> hids = row.getSet(4, Long.class);
			HashSet<Long> hids_h = (hids instanceof HashSet<?>) ? (HashSet<Long>) hids : new HashSet<>(hids);

			AdaptiveBucket buk = new AdaptiveBucket(row.getString(1), row.getString(2), row.getInt(3), hids_h);

			return buk;
		});
	}

	@Override
	public void dump(String file) {
		try {
			LineSequenceWriter writer = Lines.getLineWriter(file, false);
			javaFunctions(this.sparkInstance.getContext()).cassandraTable(databaseName, _ADAPTIVE_BUCK)
					.select(_APP_ID, _ADAPTIVE_BUCK_PKEY, _ADAPTIVE_BUCK_CKEY, _ADAPTIVE_BUCK_DEPTH, _ADAPTIVE_BUCK_HIDS)
					.map(row -> {
						return row.toString();
					}).collect().forEach(writer::writeLineNoExcept);
			writer.close();
		} catch (Exception e) {
			logger.error("Failed to dump the index.", e);
		}
	}

	@Override
	public boolean clearAll(long rid) {
		try {
			this.cassandraInstance.doWithSession(sess -> {
				sess.executeAsync(QueryBuilder.delete().from(databaseName, _ADAPTIVE_BUCK)//
						.where(eq(_APP_ID, rid)));
			});
			return true;
		} catch (Exception e) {
			logger.error("Failed to delete the index.", e);
			return false;
		}
	}

}
