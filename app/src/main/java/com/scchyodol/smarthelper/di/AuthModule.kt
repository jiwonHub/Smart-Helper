package com.scchyodol.smarthelper.di

import com.scchyodol.smarthelper.data.remote.repository.AuthRepositoryImpl
import com.scchyodol.smarthelper.domain.repository.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)   // ← 앱 전체 생명주기에 싱글톤으로 유지
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl  // ← 구현체를 인터페이스에 바인딩!
    ): AuthRepository
}
