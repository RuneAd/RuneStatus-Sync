package gg.runestatus.sync;

import com.google.gson.Gson;
import gg.runestatus.sync.data.PlayerSyncData;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Singleton
public class RuneStatusClient
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final String API_ENDPOINT = "https://api.runestatus.gg/plugin/sync";

	private final OkHttpClient httpClient;
	private final Gson gson;

	@Inject
	public RuneStatusClient(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.gson = gson;
	}

	public CompletableFuture<Boolean> syncPlayerData(PlayerSyncData data)
	{
		CompletableFuture<Boolean> future = new CompletableFuture<>();

		String json = gson.toJson(data);

		log.info("Syncing player data for user: {}", data.getUsername());
		log.info("Combat achievements data: {}", gson.toJson(data.getCombatAchievements()));

		Request request = new Request.Builder()
			.url(API_ENDPOINT)
			.post(RequestBody.create(JSON, json))
			.header("Content-Type", "application/json")
			.header("User-Agent", "RuneStatus-Sync/1.0")
			.build();

		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.error("Failed to sync player data to RuneStatus", e);
				future.complete(false);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (response)
				{
					if (response.isSuccessful())
					{
						log.debug("Successfully synced player data to RuneStatus");
						future.complete(true);
					}
					else
					{
						log.warn("RuneStatus API returned error: {} {}", response.code(), response.message());
						future.complete(false);
					}
				}
			}
		});

		return future;
	}
}
