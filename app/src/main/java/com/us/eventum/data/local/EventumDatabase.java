package com.us.eventum.data.local;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import android.content.Context;

import com.us.eventum.data.local.dao.AttendeeDao;
import com.us.eventum.data.local.dao.EventDao;
import com.us.eventum.data.local.dao.UserDao;
import com.us.eventum.data.local.entities.AttendeeEntity;
import com.us.eventum.data.local.entities.EventEntity;
import com.us.eventum.data.local.entities.UserEntity;
import com.us.eventum.data.local.converters.DateConverter;

/**
 * Base de datos Room para Eventum
 * Define la estructura de la base de datos local y proporciona acceso a los DAOs
 */
@Database(
    entities = {
        EventEntity.class,
        UserEntity.class,
        AttendeeEntity.class
    },
    version = 4,
    exportSchema = false
)
@TypeConverters({DateConverter.class})
public abstract class EventumDatabase extends RoomDatabase {
    
    // Instancia singleton de la base de datos
    private static volatile EventumDatabase INSTANCE;
    
    // Nombre de la base de datos
    private static final String DATABASE_NAME = "eventum_database";
    
    /**
     * Obtener instancia singleton de la base de datos
     */
    public static EventumDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (EventumDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        EventumDatabase.class,
                        DATABASE_NAME
                    )
                    .fallbackToDestructiveMigration() // Permite migraciones destructivas
                    .build();
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * Obtener DAO para eventos
     */
    public abstract EventDao eventDao();
    
    /**
     * Obtener DAO para usuarios
     */
    public abstract UserDao userDao();
    
    /**
     * Obtener DAO para asistentes
     */
    public abstract AttendeeDao attendeeDao();
    
    /**
     * Cerrar la base de datos
     */
    public static void closeDatabase() {
        if (INSTANCE != null && INSTANCE.isOpen()) {
            INSTANCE.close();
            INSTANCE = null;
        }
    }
}
