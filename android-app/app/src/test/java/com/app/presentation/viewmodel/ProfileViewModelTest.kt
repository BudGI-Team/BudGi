package com.app.presentation.viewmodel

import com.app.data.local.ThemeManager
import com.app.data.local.TokenManager
import com.app.domain.model.User
import com.app.domain.repository.AuthRepository
import com.app.domain.repository.SpaceRepository
import com.app.domain.repository.TransactionRepository
import com.app.security.BiometricSecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var tokenManager: TokenManager
    private lateinit var themeManager: ThemeManager
    private lateinit var spaceRepository: SpaceRepository
    private lateinit var biometricSecurityManager: BiometricSecurityManager
    private lateinit var viewModel: ProfileViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        
        transactionRepository = mock()
        authRepository = mock()
        tokenManager = mock()
        themeManager = mock()
        spaceRepository = mock()
        biometricSecurityManager = mock()

        // Setup mock behavior
        whenever(tokenManager.isBiometricEnabled).thenReturn(true)
        whenever(tokenManager.isPushEnabled).thenReturn(true)
        whenever(tokenManager.isDarkModeEnabled).thenReturn(true)
        whenever(authRepository.isLoggedIn).thenReturn(true)
        
        val mockUser = User(
            id = "1",
            name = "Test User",
            email = "test@test.com",
            username = "testuser",
            currency = "INR",
            monthlyIncome = 50000.0,
            plan = "free",
            healthScore = 85,
            isEmailVerified = true,
            avatar = "zen_saver",
            streak = 1
        )
        whenever(authRepository.currentUser).thenReturn(flowOf(mockUser))

        viewModel = ProfileViewModel(
            transactionRepository,
            authRepository,
            tokenManager,
            themeManager,
            spaceRepository,
            biometricSecurityManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is loaded from token manager and repository`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertEquals(true, state.isBiometricEnabled)
        assertEquals("Test User", state.user?.name)
        assertEquals("testuser", state.user?.username)
        assertEquals("@testuser", state.user?.handle)
        assertEquals("zen_saver", state.user?.avatar)
        assertEquals(true, state.isLoaded)
    }

    @Test
    fun `updateProfile updates state on success`() = runTest {
        val mockUser = User(
            id = "1",
            name = "Updated User",
            email = "test@test.com",
            username = "testuser",
            currency = "INR"
        )
        
        whenever(authRepository.updateProfile(name = "Updated User"))
            .thenReturn(Result.success(mockUser))

        viewModel.updateProfile("Updated User")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Profile updated successfully", state.message)
        assertEquals(true, state.updateSuccess)
        assertEquals(false, state.isUpdating)
        assertEquals("Updated User", state.user?.name)
    }

    @Test
    fun `updateFullProfile updates avatar and username on success`() = runTest {
        val mockUser = User(
            id = "1",
            name = "Updated User",
            email = "test@test.com",
            username = "newhandle",
            dateOfBirth = "2000-01-01",
            avatar = "wealthy_hype",
            currency = "USD",
            monthlyIncome = 75000.0
        )
        
        whenever(authRepository.updateProfile(
            name = "Updated User",
            username = "newhandle",
            dateOfBirth = "2000-01-01",
            avatar = "wealthy_hype",
            currency = "USD",
            monthlyIncome = 75000.0
        )).thenReturn(Result.success(mockUser))

        viewModel.updateFullProfile(
            name = "Updated User",
            username = "newhandle",
            dateOfBirth = "2000-01-01",
            avatar = "wealthy_hype",
            currency = "USD",
            monthlyIncome = 75000.0
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Profile updated successfully", state.message)
        assertEquals(true, state.updateSuccess)
        assertEquals("wealthy_hype", state.user?.avatar)
        assertEquals("@newhandle", state.user?.handle)
        assertEquals("USD", state.user?.currency)
    }
}
