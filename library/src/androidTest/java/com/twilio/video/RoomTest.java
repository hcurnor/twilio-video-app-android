/*
 * Copyright (C) 2018 Twilio, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twilio.video;

import static com.twilio.video.TestUtils.ICE_TIMEOUT;
import static org.apache.commons.lang3.RandomStringUtils.random;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import com.twilio.video.base.BaseVideoTest;
import com.twilio.video.helper.CallbackHelper;
import com.twilio.video.util.Constants;
import com.twilio.video.util.CredentialsUtils;
import com.twilio.video.util.RoomUtils;
import com.twilio.video.util.Topology;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RoomTest extends BaseVideoTest {
    private Context context;
    private String identity;
    private String roomName;
    private String token;
    private final CallbackHelper.FakeRoomListener roomListener =
            new CallbackHelper.FakeRoomListener();
    private Room room;

    @Before
    public void setup() throws InterruptedException {
        super.setup();
        context = InstrumentationRegistry.getTargetContext();
        identity = Constants.PARTICIPANT_ALICE;
        roomName = random(Constants.ROOM_NAME_LENGTH);
        Topology topology = Topology.GROUP;
        assertNotNull(RoomUtils.createRoom(roomName, topology));
        token = CredentialsUtils.getAccessToken(identity, topology);
    }

    @After
    public void teardown() throws InterruptedException {
        if (room != null && room.getState() != RoomState.DISCONNECTED) {
            roomListener.onDisconnectedLatch = new CountDownLatch(1);
            room.disconnect();
            assertTrue(
                    "Did not receive disconnect callback",
                    roomListener.onDisconnectedLatch.await(20, TimeUnit.SECONDS));
        }
        /*
         * After all participants have disconnected complete the room to clean up backend
         * resources.
         */
        if (room != null) {
            RoomUtils.completeRoom(room);
        }
        assertTrue(MediaFactory.isReleased());
    }

    @Test
    public void disconnect_shouldBeIdempotent() throws InterruptedException {
        roomListener.onConnectedLatch = new CountDownLatch(1);
        roomListener.onDisconnectedLatch = new CountDownLatch(1);

        IceOptions iceOptions =
                new IceOptions.Builder()
                        .iceServersTimeout(ICE_TIMEOUT)
                        .abortOnIceServersTimeout(true)
                        .build();
        ConnectOptions connectOptions =
                new ConnectOptions.Builder(token).roomName(roomName).iceOptions(iceOptions).build();
        room = Video.connect(context, connectOptions, roomListener);
        room.disconnect();
        room.disconnect();
        assertTrue(roomListener.onDisconnectedLatch.await(20, TimeUnit.SECONDS));
    }

    @Test
    public void getStats_canBeCalledAfterDisconnect() throws InterruptedException {
        roomListener.onConnectedLatch = new CountDownLatch(1);
        roomListener.onDisconnectedLatch = new CountDownLatch(1);

        IceOptions iceOptions =
                new IceOptions.Builder()
                        .iceServersTimeout(ICE_TIMEOUT)
                        .abortOnIceServersTimeout(true)
                        .build();
        ConnectOptions connectOptions =
                new ConnectOptions.Builder(token).roomName(roomName).iceOptions(iceOptions).build();
        room = Video.connect(context, connectOptions, roomListener);
        room.disconnect();
        room.getStats(
                statsReports -> {
                    // Do nothing
                });
        assertTrue(roomListener.onDisconnectedLatch.await(20, TimeUnit.SECONDS));
    }
}
