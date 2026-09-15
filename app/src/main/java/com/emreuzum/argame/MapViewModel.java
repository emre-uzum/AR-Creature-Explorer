package com.emreuzum.argame;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.emreuzum.argame.data.ActiveSpawn;
import com.emreuzum.argame.data.SpawnRepository;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MapViewModel extends AndroidViewModel {

    private final SpawnRepository spawnRepository;
    private final LiveData<List<ActiveSpawn>> activeSpawns;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public MapViewModel(@NonNull Application application) {
        super(application);
        spawnRepository = new SpawnRepository(application);
        activeSpawns = spawnRepository.observeActiveSpawns();
    }

    public LiveData<List<ActiveSpawn>> getActiveSpawns() {
        return activeSpawns;
    }

    public void markSpawnCaptured(String spawnId) {
        executorService.execute(() -> spawnRepository.markSpawnCaptured(spawnId));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdownNow();
    }
}
