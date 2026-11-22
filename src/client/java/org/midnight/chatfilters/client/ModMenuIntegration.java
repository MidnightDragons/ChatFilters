package org.midnight.chatfilters.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

@SuppressWarnings("unused")
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> YaclScreens.isLoaded() ? YaclScreens.create(parent) : new FilterScreen(parent);
	}
}