/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
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
 */

package net.fabricmc.fabric.test.command.client;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClientCommandTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(ClientCommandTest.class);
	private static final DynamicCommandExceptionType IS_NULL = new DynamicCommandExceptionType(x -> Component.literal("The " + x + " is null"));
	private static final SimpleCommandExceptionType UNEXECUTABLE_EXECUTED = new SimpleCommandExceptionType(Component.literal("Executed an unexecutable command!"));

	private static boolean wasTested = false;

	public static void onInitializeClient() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(ClientCommandManager.literal("test_client_command").executes(context -> {
				context.getSource().sendFeedback(Component.literal("This is a client command!"));

				if (context.getSource().getClient() == null) {
					throw IS_NULL.create("client");
				}

				if (context.getSource().getWorld() == null) {
					throw IS_NULL.create("world");
				}

				if (context.getSource().getPlayer() == null) {
					throw IS_NULL.create("player");
				}

				return 0;
			}));

			// Command with argument
			dispatcher.register(ClientCommandManager.literal("test_client_command_with_arg").then(
					ClientCommandManager.argument("number", DoubleArgumentType.doubleArg()).executes(context -> {
						double number = DoubleArgumentType.getDouble(context, "number");

						// Test error formatting
						context.getSource().sendError(Component.literal("Your number is " + number));

						return 0;
					})
			));

			// Unexecutable command
			dispatcher.register(ClientCommandManager.literal("hidden_client_command").requires(source -> false).executes(context -> {
				throw UNEXECUTABLE_EXECUTED.create();
			}));

			// Command with argument using CommandRegistryAccess
			dispatcher.register(ClientCommandManager.literal("test_client_command_with_registry_using_arg").then(
					ClientCommandManager.argument("item", ItemArgument.item(registryAccess)).executes(context -> {
						final ItemInput item = ItemArgument.getItem(context, "item");
						context.getSource().sendFeedback(item.createItemStack(1, false).getDisplayName());

						return 0;
					})
			));

			// Tests

			RootCommandNode<FabricClientCommandSource> rootNode = dispatcher.getRoot();

			// We climb the tree again
			CommandNode<FabricClientCommandSource> testClientCommand = rootNode.getChild("test_client_command");
			CommandNode<FabricClientCommandSource> testClientCommandWithArg = rootNode.getChild("test_client_command_with_arg");
			CommandNode<FabricClientCommandSource> hiddenClientCommand = rootNode.getChild("hidden_client_command");

			if (testClientCommand == null) {
				throw new AssertionError("Expected to find 'test_client_command' on the client command dispatcher. But it was not found.");
			}

			if (testClientCommandWithArg == null) {
				throw new AssertionError("Expected to find 'test_client_command_with_arg' on the client command dispatcher. But it was not found.");
			}

			if (hiddenClientCommand == null) {
				throw new AssertionError("Expected to find 'hidden_client_command' on the client command dispatcher. But it was not found.");
			}

			CommandNode<FabricClientCommandSource> numberArg = testClientCommandWithArg.getChild("number");

			if (numberArg == null) {
				throw new AssertionError("Expected to find 'number' as a child of 'test_client_command_with_arg' on the client command dispatcher. But it was not found.");
			}

			LOGGER.info("The client command tests have passed! Please make sure you execute the two commands for extra safety.");
		});

		ClientTickEvents.START_WORLD_TICK.register(world -> {
			if (wasTested) {
				return;
			}

			Minecraft client = Minecraft.getInstance();
			ClientSuggestionProvider commandSource = client.getConnection().getSuggestionsProvider();

			RootCommandNode<FabricClientCommandSource> rootNode = ClientCommandManager.getActiveDispatcher().getRoot();
			CommandNode<FabricClientCommandSource> hiddenClientCommand = rootNode.getChild("hidden_client_command");

			if (!(commandSource instanceof FabricClientCommandSource)) {
				throw new AssertionError("Client command source not a FabricClientCommandSource!");
			}

			if (hiddenClientCommand.canUse((FabricClientCommandSource) commandSource)) {
				throw new AssertionError("'hidden_client_command' should not be usable.");
			}

			LOGGER.info("The in-world client command tests have passed!");
			wasTested = true;
		});
	}
}