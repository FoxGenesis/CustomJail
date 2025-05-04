package net.foxgenesis.customjail.database;

import org.springframework.stereotype.Repository;

import net.foxgenesis.watame.data.PluginRepository;

@Repository
public interface CustomJailDatabase extends PluginRepository<CustomJailConfiguration>{

}
