package com.drivecall.contacts

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.provider.ContactsContract
import com.drivecall.models.Contact
import com.drivecall.utilities.AliasManager
import com.drivecall.utilities.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ContactRepository(private val context: Context) {

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private var isLoaded = false
    private var contentObserver: ContentObserver? = null

    init {
        registerContactObserver()
    }

    fun getContacts(): List<Contact> {
        return _contacts.value
    }

    fun loadContacts(): List<Contact> {
        if (isLoaded) {
            Logger.debug("ContactRepository", "Returning cached contacts (${_contacts.value.size})")
            return _contacts.value
        }
        Logger.service("Loading contacts from device")
        tryRegisterObserver()
        val loaded = fetchContacts()
        _contacts.value = loaded
        isLoaded = true
        Logger.service("Loaded ${loaded.size} contacts")
        return loaded
    }

    fun forceReload() {
        Logger.service("Force reloading contacts")
        isLoaded = false
        loadContacts()
    }

    private fun fetchContacts(): List<Contact> {
        val contactList = mutableListOf<Contact>()
        val contentResolver: ContentResolver = context.contentResolver

        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.Contacts.HAS_PHONE_NUMBER
        )

        val cursor = contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.Contacts.DISPLAY_NAME + " ASC"
        )

        cursor?.use { c ->
            val idIndex = c.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIndex = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            val hasPhoneIndex = c.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

            while (c.moveToNext()) {
                val id = c.getLong(idIndex)
                val name = c.getString(nameIndex) ?: continue
                val hasPhone = c.getString(hasPhoneIndex)?.toIntOrNull() ?: 0

                if (hasPhone == 0) continue

                val phoneNumber = getPhoneNumber(contentResolver, id)
                if (phoneNumber != null) {
                    val normalizedName = normalizeContactName(name)
                    val aliases = AliasManager.getAliases(normalizedName)
                    contactList.add(
                        Contact(
                            id = id,
                            name = name,
                            normalizedName = normalizedName,
                            phoneNumber = phoneNumber,
                            aliases = aliases
                        )
                    )
                }
            }
        }

        return contactList
    }

    private fun getPhoneNumber(contentResolver: ContentResolver, contactId: Long): String? {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE
        )

        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
            arrayOf(contactId.toString()),
            null
        )

        cursor?.use { c ->
            while (c.moveToNext()) {
                val number = c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                if (!number.isNullOrBlank()) {
                    val cleaned = number.replace(Regex("[\\s\\-()]"), "")
                    return cleaned
                }
            }
        }
        return null
    }

    private fun normalizeContactName(name: String): String {
        return name.trim().lowercase().replace(Regex("[\\s]+"), " ")
    }

    private fun registerContactObserver() {
        contentObserver = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                Logger.service("Contacts changed, invalidating cache")
                isLoaded = false
            }
        }
        try {
            context.contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI,
                true,
                contentObserver!!
            )
        } catch (e: SecurityException) {
            Logger.debug("ContactRepository", "Cannot register observer: ${e.message}")
        }
    }

    private fun tryRegisterObserver() {
        if (contentObserver != null) {
            try {
                context.contentResolver.unregisterContentObserver(contentObserver!!)
            } catch (_: Exception) {}
        }
        registerContactObserver()
    }

    fun cleanup() {
        contentObserver?.let {
            context.contentResolver.unregisterContentObserver(it)
        }
    }
}
