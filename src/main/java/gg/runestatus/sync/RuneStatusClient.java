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

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final RuneStatusConfig config;

	@Inject
	public RuneStatusClient(OkHttpClient httpClient, Gson gson, RuneStatusConfig config)
	{
		this.httpClient = httpClient;
		this.gson = gson;
		this.config = config;
	}

	public CompletableFuture<Boolean> syncPlayerData(PlayerSyncData data)
	{
		CompletableFuture<Boolean> future = new CompletableFuture<>();

		String json = gson.toJson(data);
		String endpoint = config.apiEndpoint();

		log.info("Syncing player data to {} for user: {}", endpoint, data.getUsername());

		Request request = new Request.Builder()
			.url(endpoint)
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
						log.info("Successfully synced player data to RuneStatus");
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
