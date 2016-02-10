package atrox.dao;

import atrox.dao.internal.EntityPointerDAO;
import atrox.model.TaggableEntity;
import atrox.model.internal.EntityPointer;
import org.cobbzilla.wizard.dao.AbstractCRUDDAO;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;

public class TaggableDAO<E extends TaggableEntity> extends AbstractCRUDDAO<E> {

    @Autowired private EntityPointerDAO entityPointerDAO;

    @Override public Object preCreate(@Valid E entity) {
        entityPointerDAO.create(new EntityPointer(entity));
        return super.preCreate(entity);
    }
}
