/*
 * Copyright (C) 2007 Esmertec AG.
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.wrappy.im.service;

import net.wrappy.im.service.IContactList;
import net.wrappy.im.model.Contact;
import net.wrappy.im.model.ImErrorInfo;

oneway interface IContactListListener {
    /**
     * Called when:
     *  <ul>
     *  <li> a contact list has been created, deleted, renamed or loaded, or
     *  <li> a contact has been added to or removed from a list, or
     *  <li> a contact has been blocked or unblocked
     *  </ul>
     *
     * @see im.ContactListListener#onContactChange(int, ContactList, Contact)
     */
    void onContactChange(int type, IContactList list, in Contact contact);

    /**
     * Called when all the contact lists have been loaded from server.
     *
     * @see im.ContactListListener#onAllContactListsLoaded()
     */
    void onAllContactListsLoaded();

    /**
     * Called when one or more contacts' presence information has updated.
     *
     * @see im.ContactListListener#onContactsPresenceUpdate(Contact[])
     */
    void onContactsPresenceUpdate(in Contact[] contacts);

    /**
     * Called when a previous contact related request has failed.
     *
     * @see im.ContactListListener#onContactError(int, ImErrorInfo, String, Contact)
     */
    void onContactError(int errorType, in ImErrorInfo error, String listName, in Contact contact);
}
