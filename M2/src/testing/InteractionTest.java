package testing;

import app_kvECS.ECSClient;
import app_kvServer.KVServer;
import common.module.ServerThread;
import org.junit.*;

import client.KVStore;
import junit.framework.TestCase;
import common.messages.KVMessage;
import common.messages.KVMessage.StatusType;

import java.io.IOException;


// originally InteractionTest
public class InteractionTest extends TestCase {

	private KVStore kvClient;
	private ECSClient ecsClient;

	public void setUp() {
		ecsClient = new ECSClient("127.0.0.1",2181,"ecs.config");
		ecsClient.addNodes(3, "None", 100);
		ecsClient.start();
		kvClient = new KVStore("localhost", 50007);
		try {
			kvClient.connect();
		} catch (Exception e) {
		}
	}

	public void tearDown() {
		kvClient.disconnect();
		ecsClient.shutdown();
	}

	@Test
	public void testPut() {
		String key = "foo2";
		String value = "bar2";
		KVMessage response = null;
		Exception ex = null;

		try {
			response = kvClient.put(key, value);
		} catch (Exception e) {
			ex = e;
		}

		assertTrue(ex == null && (response.getStatus() == StatusType.PUT_SUCCESS || response.getStatus() == StatusType.PUT_UPDATE));
	}
	
	@Test
	public void testPutDisconnected() {
		kvClient.disconnect();
		String key = "foo";
		String value = "bar";
		Exception ex = null;

		try {
			kvClient.put(key, value);
		} catch (Exception e) {
			ex = e;
		}

		assertNotNull(ex);
	}

	@Test
	public void testUpdate() {
		String key = "updateTestValue";
		String initialValue = "initial";
		String updatedValue = "updated";

		KVMessage response = null;
		Exception ex = null;

		try {
			kvClient.put(key, initialValue);
			response = kvClient.put(key, updatedValue);

		} catch (Exception e) {
			ex = e;
		}

		assertTrue(ex == null && response.getStatus() == StatusType.PUT_UPDATE
				&& response.getValue().equals(updatedValue));
	}

	@Test
	public void testDelete() {
		String key = "deleteTestValue";
		String value = "toDelete";

		KVMessage response = null;
		Exception ex = null;

		try {
			kvClient.put(key, value);
			response = kvClient.put(key, "");

		} catch (Exception e) {
			ex = e;
		}

		assertTrue(ex == null && response.getStatus() == StatusType.DELETE_SUCCESS);
	}

	@Test
	public void testGet() {
		String key = "foo";
		String value = "bar";
		KVMessage response = null;
		Exception ex = null;

			try {
				kvClient.put(key, value);
				response = kvClient.get(key);
			} catch (Exception e) {
				ex = e;
			}

		assertTrue(ex == null && response.getValue().equals("bar"));
	}

	@Test
	public void testGetUnsetValue() {
		String key = "an unset value";
		KVMessage response = null;
		Exception ex = null;

		try {
			response = kvClient.get(key);
		} catch (Exception e) {
			ex = e;
		}

		assertTrue(ex == null && response.getStatus() == StatusType.GET_ERROR);
	}
}
