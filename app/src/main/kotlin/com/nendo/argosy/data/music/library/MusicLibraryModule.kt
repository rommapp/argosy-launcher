package com.nendo.argosy.data.music.library

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MusicLibraryModule {

    @Binds
    @Singleton
    abstract fun bindMusicLibrary(impl: MusicLibraryRepository): MusicLibrary
}
